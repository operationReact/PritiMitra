import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/core/network/api_result.dart';
import 'package:pritimitra/features/messaging/application/messaging_controller.dart';
import 'package:pritimitra/features/messaging/data/messaging_api.dart';
import 'package:pritimitra/features/messaging/domain/messaging_models.dart';

const _conversationId = 'conv-1';

ChatMessage _message(String id, {bool mine = false, String body = 'hi'}) {
  return ChatMessage(
    messageId: id,
    clientMessageId: mine ? 'client-$id' : '',
    body: body,
    mine: mine,
    createdAt: '2025-01-01T00:00:00Z',
  );
}

class _FakeMessagingApi extends MessagingApi {
  _FakeMessagingApi() : super(client: null);

  final pageResults = <ApiResult<MessagePage>>[];
  final sendResults = <ApiResult<ChatMessage>>[];
  final sentClientIds = <String>[];
  int _clientIdCounter = 0;

  @override
  String newClientMessageId() => 'client-${++_clientIdCounter}';

  @override
  Future<ApiResult<MessagePage>> fetchMessages(
    String conversationId, {
    String? cursor,
    int size = 30,
  }) async {
    if (pageResults.isEmpty) {
      return const ApiSuccess(MessagePage(items: [], nextCursor: null));
    }
    return pageResults.removeAt(0);
  }

  @override
  Future<ApiResult<ChatMessage>> sendMessage({
    required String conversationId,
    required String clientMessageId,
    required String body,
  }) async {
    sentClientIds.add(clientMessageId);
    return sendResults.removeAt(0);
  }

  @override
  Future<ApiResult<int>> markRead({
    required String conversationId,
    required String messageId,
  }) async {
    return const ApiSuccess(0);
  }
}

void main() {
  late _FakeMessagingApi api;
  ProviderContainer? container;
  ProviderSubscription<ConversationState>? subscription;

  /// Opens the controller. Call only after queuing every fake response for the test.
  void open() {
    container = ProviderContainer(
      overrides: [messagingApiProvider.overrideWithValue(api)],
    );
    subscription = container!.listen(
      conversationControllerProvider(_conversationId),
      (_, _) {},
    );
  }

  ConversationController controller() =>
      container!.read(conversationControllerProvider(_conversationId).notifier);

  ConversationState state() =>
      container!.read(conversationControllerProvider(_conversationId));

  setUp(() {
    api = _FakeMessagingApi();
  });

  tearDown(() {
    subscription?.close();
    container?.dispose();
    container = null;
    subscription = null;
  });

  test('load shows the newest page and clears loading', () async {
    api.pageResults.add(
      ApiSuccess(
        MessagePage(
          items: [_message('m2'), _message('m1')],
          nextCursor: 'cursor-1',
        ),
      ),
    );
    open();

    await pumpEventQueue();

    expect(state().loading, isFalse);
    expect(state().messages.map((m) => m.messageId), ['m2', 'm1']);
    expect(state().nextCursor, 'cursor-1');
  });

  test('poll adds only unseen messages on top without duplicates', () async {
    api.pageResults.addAll([
      ApiSuccess(MessagePage(items: [_message('m1')], nextCursor: null)),
      ApiSuccess(
        MessagePage(
          items: [_message('m3'), _message('m2'), _message('m1')],
          nextCursor: null,
        ),
      ),
    ]);
    open();
    await pumpEventQueue();

    await controller().pollNewest();

    expect(state().messages.map((m) => m.messageId), ['m3', 'm2', 'm1']);
  });

  test('poll with an expired session flags sessionExpired', () async {
    api.pageResults.addAll([
      const ApiSuccess(MessagePage(items: [], nextCursor: null)),
      const ApiUnauthorized<MessagePage>(),
    ]);
    open();
    await pumpEventQueue();

    await controller().pollNewest();

    expect(state().sessionExpired, isTrue);
  });

  test('retry reuses the same clientMessageId and confirms once', () async {
    api.sendResults.addAll([
      const ApiUnavailable<ChatMessage>(statusCode: 503),
      ApiSuccess(_message('m9', mine: true, body: 'hello')),
    ]);
    open();
    await pumpEventQueue();

    await controller().send('hello');
    expect(state().outgoing.single.status, SendStatus.failed);

    final failedId = state().outgoing.single.clientMessageId;
    await controller().retry(failedId);

    expect(api.sentClientIds, [failedId, failedId]);
    expect(state().outgoing, isEmpty);
    expect(state().messages.map((m) => m.messageId), ['m9']);
  });

  test('a closed conversation response blocks further sends', () async {
    api.sendResults.add(
      const ApiUnavailable<ChatMessage>(code: 'CONVERSATION_CLOSED'),
    );
    open();
    await pumpEventQueue();

    await controller().send('still there?');

    expect(state().closed, isTrue);
    expect(state().outgoing, isEmpty);

    await controller().send('second attempt');
    expect(api.sentClientIds.length, 1);
  });
}
