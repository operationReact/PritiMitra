import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_result.dart';
import '../../../core/router/app_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../../auth/presentation/auth_form_scaffold.dart';
import '../application/messaging_controller.dart';
import '../domain/messaging_models.dart';

const _sessionExpiredMessage =
    'Your session has expired. Sign in again to continue.';

/// Lists the viewer's conversations, one per active match. Unmatching is explicit and confirmed.
class ConversationsScreen extends ConsumerWidget {
  const ConversationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(conversationsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Matches'),
        actions: [
          IconButton(
            tooltip: 'Notifications',
            icon: const Icon(Icons.notifications_none),
            onPressed: () => context.push(AppRoutes.notifications),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.refresh(conversationsProvider.future),
        child: switch (async) {
          AsyncData(:final value) => _body(context, ref, value),
          AsyncError() => _message(
            context,
            ref,
            'We could not load your matches.',
          ),
          _ => const Center(child: CircularProgressIndicator()),
        },
      ),
    );
  }

  Widget _body(
    BuildContext context,
    WidgetRef ref,
    ApiResult<ConversationPage> result,
  ) {
    return switch (result) {
      ApiSuccess(:final data) when data.items.isEmpty => _message(
        context,
        ref,
        'No matches yet. Mutual likes create a match.',
      ),
      ApiSuccess(:final data) => ListView.separated(
        padding: const EdgeInsets.all(AppSpacing.md),
        itemCount: data.items.length,
        separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.xs),
        itemBuilder: (context, index) =>
            _ConversationTile(item: data.items[index]),
      ),
      ApiUnauthorized() => _message(context, ref, _sessionExpiredMessage),
      _ => _message(context, ref, 'We could not load your matches.'),
    };
  }

  Widget _message(BuildContext context, WidgetRef ref, String text) {
    return ListView(
      children: [
        const SizedBox(height: AppSpacing.xxl),
        Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            children: [
              Text(text, textAlign: TextAlign.center),
              const SizedBox(height: AppSpacing.xs),
              TextButton(
                onPressed: () => ref.invalidate(conversationsProvider),
                child: const Text('Try again'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ConversationTile extends ConsumerWidget {
  const _ConversationTile({required this.item});

  final ConversationSummary item;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final closed = item.status != 'ACTIVE';
    return Card(
      child: ListTile(
        title: Text(closed ? 'Conversation closed' : 'Conversation'),
        subtitle: Text(
          item.lastMessagePreview ?? 'Say hello.',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (item.unreadCount > 0) Badge(label: Text('${item.unreadCount}')),
            PopupMenuButton<String>(
              tooltip: 'More',
              onSelected: (value) {
                if (value == 'unmatch') {
                  _confirmUnmatch(context, ref);
                }
              },
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'unmatch', child: Text('Unmatch')),
              ],
            ),
          ],
        ),
        onTap: closed
            ? null
            : () => context.push(
                '${AppRoutes.conversations}/${item.conversationId}',
              ),
      ),
    );
  }

  Future<void> _confirmUnmatch(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Unmatch?'),
        content: const Text(
          'The conversation will close. Messages stay available to both of you. You will need to like each other again to match.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Unmatch'),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    final result = await ref.read(messagingApiProvider).unmatch(item.matchId);
    if (!context.mounted) {
      return;
    }
    if (result is ApiSuccess) {
      ref.invalidate(conversationsProvider);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('We could not unmatch. Try again.')),
      );
    }
  }
}

/// One conversation. Shows confirmed history newest first, plus pending and failed sends.
class ConversationScreen extends ConsumerStatefulWidget {
  const ConversationScreen({required this.conversationId, super.key});

  final String conversationId;

  @override
  ConsumerState<ConversationScreen> createState() => _ConversationScreenState();
}

class _ConversationScreenState extends ConsumerState<ConversationScreen> {
  final _controller = TextEditingController();
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_maybeLoadMore);
  }

  @override
  void dispose() {
    _scroll.dispose();
    _controller.dispose();
    super.dispose();
  }

  void _maybeLoadMore() {
    if (_scroll.hasClients && _scroll.position.extentAfter < 200) {
      ref
          .read(conversationControllerProvider(widget.conversationId).notifier)
          .loadMore();
    }
  }

  Future<void> _send() async {
    final text = _controller.text;
    final error = validateMessageBody(text);
    if (error != null) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
      return;
    }
    _controller.clear();
    await ref
        .read(conversationControllerProvider(widget.conversationId).notifier)
        .send(text);
  }

  Future<void> _report(ChatMessage message) async {
    final category = await showDialog<ReportCategory>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Report this message'),
        children: [
          for (final option in ReportCategory.values)
            SimpleDialogOption(
              onPressed: () => Navigator.of(context).pop(option),
              child: Text(option.label),
            ),
        ],
      ),
    );
    if (category == null || !mounted) {
      return;
    }
    final result = await ref
        .read(messagingApiProvider)
        .report(
          targetType: ReportTargetType.message,
          targetId: message.messageId,
          category: category,
        );
    if (!mounted) {
      return;
    }
    final text = result is ApiSuccess
        ? 'Thanks. Your report is with the safety team.'
        : 'We could not send the report. Try again.';
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    final provider = conversationControllerProvider(widget.conversationId);
    final state = ref.watch(provider);
    final notifier = ref.read(provider.notifier);

    if (state.sessionExpired) {
      return AuthFormScaffold(
        title: 'Session expired',
        subtitle: _sessionExpiredMessage,
        children: [
          FilledButton(
            onPressed: () => context.go(AppRoutes.signIn),
            child: const Text('Sign in'),
          ),
        ],
      );
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Conversation')),
      body: Column(
        children: [
          Expanded(child: _buildList(state, notifier)),
          if (state.closed)
            const Padding(
              padding: EdgeInsets.all(AppSpacing.md),
              child: Text(
                'This conversation is closed. You can no longer send messages.',
                textAlign: TextAlign.center,
              ),
            )
          else
            _Composer(controller: _controller, onSend: _send),
        ],
      ),
    );
  }

  Widget _buildList(ConversationState state, ConversationController notifier) {
    if (state.loading && state.messages.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.loadError != null && state.messages.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(state.loadError!),
            TextButton(
              onPressed: notifier.load,
              child: const Text('Try again'),
            ),
          ],
        ),
      );
    }
    final rows = <Widget>[
      for (final o in state.outgoing)
        _OutgoingBubble(
          message: o,
          onRetry: () => notifier.retry(o.clientMessageId),
        ),
      for (final m in state.messages)
        _MessageBubble(message: m, onReport: () => _report(m)),
      if (state.loadingMore)
        const Padding(
          padding: EdgeInsets.all(AppSpacing.xs),
          child: Center(child: CircularProgressIndicator()),
        ),
    ];
    if (rows.isEmpty) {
      return const Center(child: Text('Say hello to start the conversation.'));
    }
    return ListView(
      controller: _scroll,
      reverse: true,
      padding: const EdgeInsets.all(AppSpacing.md),
      children: rows,
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message, required this.onReport});

  final ChatMessage message;
  final VoidCallback onReport;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Align(
      alignment: message.mine ? Alignment.centerRight : Alignment.centerLeft,
      child: GestureDetector(
        onLongPress: message.mine ? null : onReport,
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: AppSpacing.xxs),
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.md,
            vertical: AppSpacing.xs,
          ),
          decoration: BoxDecoration(
            color: message.mine
                ? scheme.primaryContainer
                : scheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Text(message.body),
        ),
      ),
    );
  }
}

class _OutgoingBubble extends StatelessWidget {
  const _OutgoingBubble({required this.message, required this.onRetry});

  final OutgoingMessage message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final failed = message.status == SendStatus.failed;
    final scheme = Theme.of(context).colorScheme;
    return Align(
      alignment: Alignment.centerRight,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxs),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.md,
                vertical: AppSpacing.xs,
              ),
              decoration: BoxDecoration(
                color: scheme.primaryContainer.withValues(
                  alpha: failed ? 0.5 : 0.8,
                ),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Text(message.body),
            ),
            if (failed)
              TextButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: const Text('Not sent. Retry'),
              )
            else
              const Text('Sending…'),
          ],
        ),
      ),
    );
  }
}

class _Composer extends StatelessWidget {
  const _Composer({required this.controller, required this.onSend});

  final TextEditingController controller;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.md,
          0,
          AppSpacing.md,
          AppSpacing.md,
        ),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: controller,
                minLines: 1,
                maxLines: 4,
                maxLength: maxMessageLength,
                textInputAction: TextInputAction.send,
                onSubmitted: (_) => onSend(),
                decoration: const InputDecoration(
                  hintText: 'Write a message',
                  counterText: '',
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.xs),
            IconButton.filled(
              tooltip: 'Send',
              onPressed: onSend,
              icon: const Icon(Icons.send),
            ),
          ],
        ),
      ),
    );
  }
}

/// Lists in-app notifications with read state. Items are marked read only after the server confirms.
class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(notificationsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          TextButton(
            onPressed: () async {
              await ref.read(messagingApiProvider).markAllNotificationsRead();
              ref.invalidate(notificationsProvider);
            },
            child: const Text('Mark all read'),
          ),
        ],
      ),
      body: switch (async) {
        AsyncData(:final value) => switch (value) {
          ApiSuccess(:final data) when data.items.isEmpty => const Center(
            child: Text('No notifications yet.'),
          ),
          ApiSuccess(:final data) => ListView.builder(
            itemCount: data.items.length,
            itemBuilder: (context, index) {
              final item = data.items[index];
              return ListTile(
                leading: Icon(
                  item.kind == NotificationKind.match
                      ? Icons.favorite
                      : Icons.chat_bubble_outline,
                ),
                title: Text(
                  item.kind == NotificationKind.match
                      ? 'You have a new match'
                      : 'New message',
                ),
                subtitle: Text(item.read ? 'Read' : 'Unread'),
                onTap: () async {
                  if (!item.read) {
                    await ref
                        .read(messagingApiProvider)
                        .markNotificationRead(item.notificationId);
                    ref.invalidate(notificationsProvider);
                  }
                  final conversationId = item.conversationId;
                  if (conversationId != null && context.mounted) {
                    context.push('${AppRoutes.conversations}/$conversationId');
                  }
                },
              );
            },
          ),
          ApiUnauthorized() => const Center(
            child: Text(_sessionExpiredMessage),
          ),
          _ => Center(
            child: TextButton(
              onPressed: () => ref.invalidate(notificationsProvider),
              child: const Text('Could not load notifications. Try again'),
            ),
          ),
        },
        AsyncError() => Center(
          child: TextButton(
            onPressed: () => ref.invalidate(notificationsProvider),
            child: const Text('Could not load notifications. Try again'),
          ),
        ),
        _ => const Center(child: CircularProgressIndicator()),
      },
    );
  }
}
