import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_result.dart';
import '../../../core/theme/app_tokens.dart';
import '../../auth/application/auth_controller.dart';
import '../data/community_api.dart';

final communityApiProvider = Provider<CommunityApi>((ref) => CommunityApi(client: ref.watch(apiClientProvider)));

class CommunityScreen extends ConsumerStatefulWidget {
  const CommunityScreen({super.key});
  @override
  ConsumerState<CommunityScreen> createState() => _CommunityScreenState();
}
class _CommunityScreenState extends ConsumerState<CommunityScreen> {
  final _draft = TextEditingController();
  List<CommunityGroup> _groups = const [];
  List<CommunityPost> _posts = const [];
  String? _selectedId, _cursor, _error, _notice;
  bool _loadingGroups = true, _loadingPosts = false, _saving = false;
  CommunityGroup? get _selected {
    for (final group in _groups) { if (group.publicId == _selectedId) return group; }
    return null;
  }
  @override
  void initState() { super.initState(); Future<void>.microtask(_loadGroups); }
  @override
  void dispose() { _draft.dispose(); super.dispose(); }

  Future<void> _loadGroups() async {
    setState(() { _loadingGroups = true; _error = null; });
    final result = await ref.read(communityApiProvider).fetchGroups();
    if (!mounted) return;
    switch (result) {
      case ApiSuccess(:final data):
        final id = _selectedId != null && data.any((g) => g.publicId == _selectedId)
          ? _selectedId : data.isEmpty ? null : data.first.publicId;
        setState(() { _groups = data; _selectedId = id; _loadingGroups = false; });
        final group = _selected;
        if (group != null && group.joined) {
          await _loadPosts(group.publicId);
        } else {
          setState(() { _posts = const []; _cursor = null; _loadingPosts = false; });
        }
      case ApiUnauthorized():
        setState(() { _loadingGroups = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable():
        setState(() { _loadingGroups = false; _error = 'We could not load communities. Check your connection and retry.'; });
    }
  }
  Future<void> _loadPosts(String groupId, {String? cursor, bool append = false}) async {
    setState(() { _loadingPosts = true; _error = null; });
    final result = await ref.read(communityApiProvider).fetchPosts(groupId, cursor: cursor);
    if (!mounted) return;
    switch (result) {
      case ApiSuccess(:final data):
        setState(() { _posts = append ? [..._posts, ...data.items] : data.items; _cursor = data.nextCursor; _loadingPosts = false; });
      case ApiUnauthorized():
        setState(() { _loadingPosts = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable():
        setState(() { _loadingPosts = false; _error = 'We could not load posts. Retry when you are online.'; });
    }
  }
  Future<void> _select(CommunityGroup group) async {
    setState(() { _selectedId = group.publicId; _posts = const []; _cursor = null; _notice = null; });
    if (group.joined) await _loadPosts(group.publicId);
  }
  Future<void> _membership(CommunityGroup group) async {
    setState(() { _saving = true; _error = null; _notice = null; });
    final api = ref.read(communityApiProvider);
    final result = group.joined ? await api.leave(group.publicId) : await api.join(group.publicId);
    if (!mounted) return;
    switch (result) {
      case ApiSuccess():
        setState(() {
          _groups = _groups.map((g) => g.publicId != group.publicId ? g : CommunityGroup(
            publicId: g.publicId, slug: g.slug, name: g.name, description: g.description,
            joined: !group.joined, memberCount: group.joined ? (g.memberCount > 0 ? g.memberCount - 1 : 0) : g.memberCount + 1,
          )).toList(growable: false);
          _saving = false;
          _notice = group.joined ? 'You left ' + group.name + '.' : 'You joined ' + group.name + '.';
          if (group.joined && _selectedId == group.publicId) { _posts = const []; _cursor = null; }
        });
        if (!group.joined) await _loadPosts(group.publicId);
      case ApiUnauthorized():
        setState(() { _saving = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable():
        setState(() { _saving = false; _error = 'Membership could not be updated. Please retry.'; });
    }
  }
  Future<void> _createPost() async {
    final group = _selected;
    if (group == null || !group.joined || _draft.text.trim().isEmpty) return;
    setState(() { _saving = true; _error = null; _notice = null; });
    final result = await ref.read(communityApiProvider).createPost(group.publicId, _draft.text);
    if (!mounted) return;
    switch (result) {
      case ApiSuccess():
        _draft.clear(); setState(() { _saving = false; _notice = 'Your post was published.'; });
        await _loadPosts(group.publicId);
      case ApiUnauthorized():
        setState(() { _saving = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable():
        setState(() { _saving = false; _error = 'Your post could not be published. Check the text and retry.'; });
    }
  }
  Future<void> _react(CommunityPost post, String reaction) async {
    setState(() { _saving = true; _error = null; });
    final api = ref.read(communityApiProvider);
    final result = post.myReaction == reaction ? await api.removeReaction(post.postPublicId) : await api.react(post.postPublicId, reaction);
    if (!mounted) return;
    switch (result) {
      case ApiSuccess():
        setState(() => _saving = false);
        final group = _selected; if (group != null) await _loadPosts(group.publicId);
      case ApiUnauthorized():
        setState(() { _saving = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable():
        setState(() { _saving = false; _error = 'Reaction could not be saved.'; });
    }
  }
  Future<void> _remove(CommunityPost post) async {
    final yes = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const Text('Remove post?'), content: const Text('This removes the post from the community feed.'),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Keep post')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Remove')),
      ],
    ));
    if (yes != true || !mounted) return;
    setState(() { _saving = true; _error = null; });
    final result = await ref.read(communityApiProvider).removePost(post.postPublicId);
    if (!mounted) return;
    switch (result) {
      case ApiSuccess():
        setState(() { _saving = false; _notice = 'Your post was removed.'; });
        final group = _selected; if (group != null) await _loadPosts(group.publicId);
      case ApiUnauthorized():
        setState(() { _saving = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable():
        setState(() { _saving = false; _error = 'The post could not be removed.'; });
    }
  }
  Future<void> _report(CommunityPost post) async {
    final yes = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const Text('Report post?'), content: const Text('The report is private. The moderation review queue is being implemented separately.'),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Report')),
      ],
    ));
    if (yes != true || !mounted) return;
    setState(() { _saving = true; _error = null; });
    final result = await ref.read(communityApiProvider).reportPost(post.postPublicId);
    if (!mounted) return;
    switch (result) {
      case ApiSuccess(): setState(() { _saving = false; _notice = 'Report submitted privately.'; });
      case ApiUnauthorized(): setState(() { _saving = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable(): setState(() { _saving = false; _error = 'Report could not be submitted.'; });
    }
  }

  @override
  Widget build(BuildContext context) {
    final selected = _selected;
    return Scaffold(
      appBar: AppBar(title: const Text('Community'), actions: [
        IconButton(tooltip: 'Refresh', onPressed: _loadingGroups ? null : _loadGroups, icon: const Icon(Icons.refresh)),
      ]),
      body: RefreshIndicator(
        onRefresh: _loadGroups,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.all(AppSpacing.md),
          children: [
            const Text('Find your people', style: TextStyle(fontWeight: FontWeight.w800)),
            const SizedBox(height: AppSpacing.xs),
            const Text('Choose a community', style: TextStyle(fontSize: 23, fontWeight: FontWeight.w800)),
            const SizedBox(height: AppSpacing.xs),
            const Text('Joining is optional. Only members can read and post in a group.'),
            const SizedBox(height: AppSpacing.sm),
            if (_loadingGroups) const LinearProgressIndicator(),
            for (final group in _groups)
              Card(child: Padding(padding: const EdgeInsets.all(AppSpacing.sm), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                ListTile(contentPadding: EdgeInsets.zero, title: Text(group.name, style: const TextStyle(fontWeight: FontWeight.w800)),
                  subtitle: Text(group.description + '\n' + group.memberCount.toString() + ' members'),
                  selected: group.publicId == _selectedId, onTap: () => _select(group)),
                Row(children: [
                  OutlinedButton(onPressed: _saving ? null : () => _membership(group), child: Text(group.joined ? 'Leave' : 'Join')),
                  const SizedBox(width: AppSpacing.xs),
                  if (group.publicId == _selectedId && group.joined) const Text('Member', style: TextStyle(fontWeight: FontWeight.w700)),
                ]),
              ]))),
            const Divider(height: AppSpacing.xxl),
            Text(selected?.name ?? 'Community feed', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
            if (selected == null) const Text('No communities are available.')
            else if (!selected.joined) const Text('Join this community to read posts and participate.')
            else ...[
              const SizedBox(height: AppSpacing.sm),
              TextField(controller: _draft, maxLength: 2000, maxLines: 3, decoration: const InputDecoration(
                labelText: 'Share something with the group', hintText: 'Keep it kind and respectful.', border: OutlineInputBorder())),
              Align(alignment: Alignment.centerRight, child: FilledButton(onPressed: _saving ? null : _createPost, child: const Text('Publish post'))),
              const SizedBox(height: AppSpacing.sm),
              if (_loadingPosts && _posts.isEmpty) const LinearProgressIndicator(),
              if (!_loadingPosts && _posts.isEmpty) const Text('No posts yet. Start the conversation.'),
              for (final post in _posts)
                Card(child: Padding(padding: const EdgeInsets.all(AppSpacing.md), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(post.displayName, style: const TextStyle(fontWeight: FontWeight.w800)),
                  Text(MaterialLocalizations.of(context).formatMediumDate(post.createdAt.toLocal()), style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(height: AppSpacing.xs), SelectableText(post.body),
                  Wrap(spacing: AppSpacing.xs, runSpacing: AppSpacing.xxs, children: [
                    OutlinedButton(onPressed: _saving ? null : () => _react(post, 'LIKE'), child: Text('Like ' + post.likeCount.toString())),
                    OutlinedButton(onPressed: _saving ? null : () => _react(post, 'HEART'), child: Text('Heart ' + post.heartCount.toString())),
                    OutlinedButton(onPressed: _saving ? null : () => _react(post, 'SUPPORT'), child: Text('Support ' + post.supportCount.toString())),
                    TextButton(onPressed: _saving ? null : () => post.mine ? _remove(post) : _report(post), child: Text(post.mine ? 'Remove' : 'Report')),
                  ]),
                ]))),
              if (_cursor != null) Align(alignment: Alignment.center, child: OutlinedButton(
                onPressed: _loadingPosts ? null : () => _loadPosts(selected.publicId, cursor: _cursor, append: true), child: const Text('Load more'))),
            ],
            if (_error != null) Padding(padding: const EdgeInsets.only(top: AppSpacing.sm),
              child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error))),
            if (_notice != null) Padding(padding: const EdgeInsets.only(top: AppSpacing.sm), child: Text(_notice!)),
          ],
        ),
      ),
    );
  }
}
