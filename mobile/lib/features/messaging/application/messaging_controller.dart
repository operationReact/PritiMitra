import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_result.dart';
import '../../auth/application/auth_controller.dart';
import '../data/messaging_api.dart';
import '../domain/messaging_models.dart';

final messagingApiProvider = Provider<MessagingApi>((ref) {
  return MessagingApi(client: ref.watch(apiClientProvider));
});

final conversationsProvider =
    FutureProvider.autoDispose<ApiResult<ConversationPage>>((ref) {
      return ref.watch(messagingApiProvider).fetchConversations();
    });

final notificationsProvider =
    FutureProvider.autoDispose<ApiResult<NotificationPage>>((ref) {
      return ref.watch(messagingApiProvider).fetchNotifications();
    });

enum SendStatus { pending, failed }

/// A message the viewer has submitted but the server has not confirmed yet. Kept so a retry reuses the same ID.
class OutgoingMessage {
  const OutgoingMessage({
    required this.clientMessageId,
    required this.body,
    required this.status,
  });

  final String clientMessageId;
  final String body;
  final SendStatus status;

  OutgoingMessage withStatus(SendStatus next) => OutgoingMessage(
    clientMessageId: clientMessageId,
    body: body,
    status: next,
  );
}

class ConversationState {
  const ConversationState({
    this.loading = true,
    this.loadingMore = false,
    this.messages = const [],
    this.nextCursor,
    this.outgoing = const [],
    this.loadError,
    this.sessionExpired = false,
    this.closed = false,
    this.otherProfilePublicId,
    this.blocking = false,
  });

  final bool loading;
  final bool loadingMore;

  /// Confirmed messages, newest first.
  final List<ChatMessage> messages;
  final String? nextCursor;

  /// Pending or failed sends, shown above the confirmed history.
  final List<OutgoingMessage> outgoing;
  final String? loadError;
  final bool sessionExpired;

  /// True once the server reports the conversation as closed (unmatched or blocked).
  final bool closed;

  /// The other participant's public profile ID, used only for the block request.
  final String? otherProfilePublicId;
  final bool blocking;

  ConversationState copyWith({
    bool? loading,
    bool? loadingMore,
    List<ChatMessage>? messages,
    String? nextCursor,
    bool clearCursor = false,
    List<OutgoingMessage>? outgoing,
    String? loadError,
    bool clearError = false,
    bool? sessionExpired,
    bool? closed,
    String? otherProfilePublicId,
    bool? blocking,
  }) {
    return ConversationState(
      loading: loading ?? this.loading,
      loadingMore: loadingMore ?? this.loadingMore,
      messages: messages ?? this.messages,
      nextCursor: clearCursor ? null : (nextCursor ?? this.nextCursor),
      outgoing: outgoing ?? this.outgoing,
      loadError: clearError ? null : (loadError ?? this.loadError),
      sessionExpired: sessionExpired ?? this.sessionExpired,
      closed: closed ?? this.closed,
      otherProfilePublicId: otherProfilePublicId ?? this.otherProfilePublicId,
      blocking: blocking ?? this.blocking,
    );
  }
}

enum BlockOutcome { blocked, failed, sessionExpired }

/// Owns one open conversation. Confirmed messages come only from server responses.
final conversationControllerProvider = NotifierProvider.autoDispose
    .family<ConversationController, ConversationState, String>(
      ConversationController.new,
    );

class ConversationController extends Notifier<ConversationState> {
  ConversationController(this.conversationId);

  final String conversationId;

  static const Duration _pollInterval = Duration(seconds: 5);
  static const Duration _maxPollBackoff = Duration(seconds: 60);

  Timer? _pollTimer;
  Duration _pollDelay = _pollInterval;
  bool _polling = false;
  bool _disposed = false;

  /// Position of the last accepted catch-up response. Only advanced after the server accepts a page.
  String? _catchUpCursor;

  @override
  ConversationState build() {
    ref.onDispose(() {
      _disposed = true;
      _pollTimer?.cancel();
    });
    Future<void>.microtask(load);
    Future<void>.microtask(_loadDetail);
    _schedulePoll();
    return const ConversationState();
  }

  MessagingApi get _api => ref.read(messagingApiProvider);

  void _schedulePoll() {
    _pollTimer?.cancel();
    _pollTimer = Timer(_pollDelay, () async {
      await pollNewest();
      if (!_disposed && !state.sessionExpired) {
        _schedulePoll();
      }
    });
  }

  /// Fetches every committed message after the last accepted cursor, oldest first, draining all pages.
  /// The cursor advances only after a successful response, so a failed poll retries from the same position.
  Future<void> pollNewest() async {
    if (_polling || _disposed) {
      return;
    }
    _polling = true;
    try {
      final cursor = _catchUpCursor;
      if (cursor == null) {
        await _seedCursor();
        return;
      }
      var current = cursor;
      var hasMore = true;
      while (hasMore && !_disposed) {
        final result = await _api.fetchNewerMessages(
          conversationId,
          cursor: current,
        );
        if (_disposed) {
          return;
        }
        switch (result) {
          case ApiSuccess(:final data):
            _pollDelay = _pollInterval;
            _prependUnseen(data.items.reversed);
            current = data.cursor.isEmpty ? current : data.cursor;
            _catchUpCursor = current;
            hasMore = data.hasMore && data.cursor != cursor;
          case ApiUnauthorized():
            state = state.copyWith(sessionExpired: true);
            return;
          case ApiForbidden() || ApiUnavailable():
            _pollDelay = _nextBackoff(_pollDelay);
            return;
        }
      }
    } finally {
      _polling = false;
    }
  }

  /// Used while no cursor exists yet (an empty conversation at load time). The newest page seeds the cursor.
  Future<void> _seedCursor() async {
    final result = await _api.fetchMessages(conversationId);
    if (_disposed) {
      return;
    }
    switch (result) {
      case ApiSuccess(:final data):
        _pollDelay = _pollInterval;
        _prependUnseen(data.items);
        _catchUpCursor = data.latestCursor ?? _catchUpCursor;
      case ApiUnauthorized():
        state = state.copyWith(sessionExpired: true);
      case ApiForbidden() || ApiUnavailable():
        _pollDelay = _nextBackoff(_pollDelay);
    }
  }

  /// Adds messages not yet shown. [newestFirst] must be ordered newest first, matching [ConversationState.messages].
  void _prependUnseen(Iterable<ChatMessage> newestFirst) {
    final known = state.messages.map((m) => m.messageId).toSet();
    final fresh = newestFirst
        .where((m) => known.add(m.messageId))
        .toList(growable: false);
    if (fresh.isNotEmpty) {
      state = state.copyWith(messages: [...fresh, ...state.messages]);
    }
  }

  static Duration _nextBackoff(Duration current) {
    final doubled = current * 2;
    return doubled > _maxPollBackoff ? _maxPollBackoff : doubled;
  }

  Future<void> _loadDetail() async {
    final result = await _api.fetchConversation(conversationId);
    if (_disposed) {
      return;
    }
    if (result case ApiSuccess(:final data)) {
      state = state.copyWith(
        otherProfilePublicId: data.otherProfilePublicId,
        closed: state.closed || data.status != 'ACTIVE',
      );
    }
  }

  Future<void> load() async {
    state = state.copyWith(loading: true, clearError: true);
    final result = await _api.fetchMessages(conversationId);
    if (_disposed) {
      return;
    }
    switch (result) {
      case ApiSuccess(:final data):
        state = state.copyWith(
          loading: false,
          messages: data.items,
          nextCursor: data.nextCursor,
          clearCursor: data.nextCursor == null,
        );
        _catchUpCursor = data.latestCursor ?? _catchUpCursor;
        _markNewestRead();
      case ApiUnauthorized():
        state = state.copyWith(loading: false, sessionExpired: true);
      case ApiForbidden() || ApiUnavailable():
        state = state.copyWith(
          loading: false,
          loadError: 'We could not load this conversation.',
        );
    }
  }

  Future<void> loadMore() async {
    final cursor = state.nextCursor;
    if (cursor == null || state.loadingMore) {
      return;
    }
    state = state.copyWith(loadingMore: true);
    final result = await _api.fetchMessages(conversationId, cursor: cursor);
    switch (result) {
      case ApiSuccess(:final data):
        final known = state.messages.map((m) => m.messageId).toSet();
        final older = data.items.where((m) => !known.contains(m.messageId));
        state = state.copyWith(
          loadingMore: false,
          messages: [...state.messages, ...older],
          nextCursor: data.nextCursor,
          clearCursor: data.nextCursor == null,
        );
      case ApiUnauthorized():
        state = state.copyWith(loadingMore: false, sessionExpired: true);
      case ApiForbidden() || ApiUnavailable():
        state = state.copyWith(loadingMore: false);
    }
  }

  /// Submits a new message with a fresh client ID.
  Future<void> send(String rawBody) async {
    if (validateMessageBody(rawBody) != null || state.closed) {
      return;
    }
    final body = rawBody.trim();
    final id = _api.newClientMessageId();
    state = state.copyWith(
      outgoing: [
        ...state.outgoing,
        OutgoingMessage(
          clientMessageId: id,
          body: body,
          status: SendStatus.pending,
        ),
      ],
    );
    await _transmit(id, body);
  }

  /// Retries a failed send with the same client ID, so the server stores at most one copy.
  Future<void> retry(String clientMessageId) async {
    final item = state.outgoing
        .where((o) => o.clientMessageId == clientMessageId)
        .firstOrNull;
    if (item == null) {
      return;
    }
    _replaceOutgoing(item.withStatus(SendStatus.pending));
    await _transmit(item.clientMessageId, item.body);
  }

  Future<void> _transmit(String clientMessageId, String body) async {
    final result = await _api.sendMessage(
      conversationId: conversationId,
      clientMessageId: clientMessageId,
      body: body,
    );
    switch (result) {
      case ApiSuccess(:final data):
        final alreadyKnown = state.messages.any(
          (m) => m.messageId == data.messageId,
        );
        state = state.copyWith(
          messages: alreadyKnown ? state.messages : [data, ...state.messages],
          outgoing: state.outgoing
              .where((o) => o.clientMessageId != clientMessageId)
              .toList(growable: false),
        );
        _markNewestRead();
      case ApiUnauthorized():
        _markOutgoingFailed(clientMessageId, body);
        state = state.copyWith(sessionExpired: true);
      case ApiUnavailable(:final code) when code == 'CONVERSATION_CLOSED':
        state = state.copyWith(
          closed: true,
          outgoing: state.outgoing
              .where((o) => o.clientMessageId != clientMessageId)
              .toList(growable: false),
        );
      case ApiForbidden() || ApiUnavailable():
        _markOutgoingFailed(clientMessageId, body);
    }
  }

  void _markOutgoingFailed(String clientMessageId, String body) {
    final current = state.outgoing
        .where((o) => o.clientMessageId == clientMessageId)
        .firstOrNull;
    final next =
        (current ??
                OutgoingMessage(
                  clientMessageId: clientMessageId,
                  body: body,
                  status: SendStatus.pending,
                ))
            .withStatus(SendStatus.failed);
    _replaceOutgoing(next);
  }

  void _replaceOutgoing(OutgoingMessage next) {
    state = state.copyWith(
      outgoing: [
        for (final o in state.outgoing)
          o.clientMessageId == next.clientMessageId ? next : o,
      ],
    );
  }

  /// Blocks the other participant after confirmation. The conversation closes only when the server confirms.
  Future<BlockOutcome> blockParticipant() async {
    final profileId = state.otherProfilePublicId;
    if (profileId == null || state.blocking || state.closed) {
      return BlockOutcome.failed;
    }
    state = state.copyWith(blocking: true);
    final result = await _api.block(profileId);
    if (_disposed) {
      return BlockOutcome.failed;
    }
    switch (result) {
      case ApiSuccess():
        state = state.copyWith(blocking: false, closed: true);
        ref.invalidate(conversationsProvider);
        return BlockOutcome.blocked;
      case ApiUnauthorized():
        state = state.copyWith(blocking: false, sessionExpired: true);
        return BlockOutcome.sessionExpired;
      case ApiForbidden() || ApiUnavailable():
        state = state.copyWith(blocking: false);
        return BlockOutcome.failed;
    }
  }

  void _markNewestRead() {
    if (state.messages.isEmpty) {
      return;
    }
    // Fire and forget: a failed read marker only delays the unread badge.
    _api.markRead(
      conversationId: conversationId,
      messageId: state.messages.first.messageId,
    );
  }
}
