import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_result.dart';
import '../../../core/theme/app_tokens.dart';
import '../../auth/application/auth_controller.dart';
import '../data/blind_date_api.dart';

final blindDateApiProvider = Provider<BlindDateApi>((ref) => BlindDateApi(client: ref.watch(apiClientProvider)));

class BlindDateScreen extends ConsumerStatefulWidget {
  const BlindDateScreen({super.key});
  @override
  ConsumerState<BlindDateScreen> createState() => _BlindDateScreenState();
}
class _BlindDateScreenState extends ConsumerState<BlindDateScreen> {
  List<BlindDateBooking> _bookings = const [];
  DateTime _slot = _defaultSlot();
  bool _loading = true, _saving = false;
  String? _error, _notice;

  static DateTime _defaultSlot() {
    final start = DateTime.now().toUtc().add(const Duration(hours: 6, minutes: 30));
    final rounded = ((start.minute + 14) ~/ 15) * 15;
    return DateTime(start.year, start.month, start.day + (rounded >= 60 ? 1 : 0),
      rounded >= 60 ? 0 : start.hour, rounded >= 60 ? rounded - 60 : rounded);
  }
  DateTime _slotAsIstWallTime() => DateTime.utc(_slot.year, _slot.month, _slot.day, _slot.hour, _slot.minute);
  DateTime _slotAsUtcInstant() => _slotAsIstWallTime().subtract(const Duration(hours: 5, minutes: 30));
  DateTime _utcToIstWallTime(DateTime value) {
    final utc = value.toUtc();
    return DateTime.utc(utc.year, utc.month, utc.day, utc.hour + 5, utc.minute + 30);
  }
  @override
  void initState() { super.initState(); Future<void>.microtask(_load); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final result = await ref.read(blindDateApiProvider).fetchMine();
    if (!mounted) return;
    switch (result) {
      case ApiSuccess(:final data): setState(() { _bookings = data; _loading = false; });
      case ApiUnauthorized(): setState(() { _loading = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable(): setState(() { _loading = false; _error = 'We could not load your schedule. Please retry.'; });
    }
  }
  Future<void> _chooseDate() async {
    final today = DateTime.now();
    final chosen = await showDatePicker(context: context, initialDate: _slot,
      firstDate: today, lastDate: today.add(const Duration(days: 14)));
    if (chosen == null || !mounted) return;
    setState(() { _slot = DateTime(chosen.year, chosen.month, chosen.day, _slot.hour, _slot.minute); });
  }
  Future<void> _chooseTime() async {
    final chosen = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(_slot));
    if (chosen == null || !mounted) return;
    setState(() { _slot = DateTime(_slot.year, _slot.month, _slot.day, chosen.hour, chosen.minute); });
  }
  Future<void> _optIn() async {
    final instant = _slotAsUtcInstant();
    if (_slot.minute % 15 != 0) {
      setState(() => _error = 'Choose :00, :15, :30, or :45.'); return;
    }
    if (instant.isBefore(DateTime.now().toUtc().add(const Duration(minutes: 16))) ||
        instant.isAfter(DateTime.now().toUtc().add(const Duration(days: 14)))) {
      setState(() => _error = 'Choose a slot 15 minutes to 14 days from now.'); return;
    }
    setState(() { _saving = true; _error = null; _notice = null; });
    final result = await ref.read(blindDateApiProvider).optIn(_slotAsIstWallTime());
    if (!mounted) return;
    switch (result) {
      case ApiSuccess():
        setState(() { _saving = false; _notice = 'Your opt-in is saved. Pairing requires another eligible member to choose the same slot.'; });
        await _load();
      case ApiUnauthorized(): setState(() { _saving = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable(): setState(() { _saving = false; _error = 'The slot could not be booked. Check its date and time.'; });
    }
  }
  Future<void> _changeBooking(BlindDateBooking booking) async {
    final cancelSession = booking.status == 'PAIRED' && booking.sessionId != null && booking.sessionStatus == 'SCHEDULED';
    if (cancelSession) {
      final confirm = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
        title: const Text('Cancel scheduled session?'),
        content: const Text('Cancel this future session for both participants?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Keep session')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Cancel session')),
        ],
      ));
      if (confirm != true || !mounted) return;
    }
    setState(() { _saving = true; _error = null; _notice = null; });
    final api = ref.read(blindDateApiProvider);
    final result = cancelSession ? await api.cancelSession(booking.sessionId!) : await api.withdraw(booking.optInId);
    if (!mounted) return;
    switch (result) {
      case ApiSuccess():
        setState(() { _saving = false; _notice = cancelSession ? 'Session cancelled.' : 'Opt-in withdrawn.'; });
        await _load();
      case ApiUnauthorized(): setState(() { _saving = false; _error = 'Your session has expired. Sign in again.'; });
      case ApiForbidden() || ApiUnavailable(): setState(() { _saving = false; _error = 'Schedule could not be updated. Please retry.'; });
    }
  }
  String _label(DateTime utc) {
    final ist = _utcToIstWallTime(utc);
    return MaterialLocalizations.of(context).formatMediumDate(ist) + ' · ' +
      MaterialLocalizations.of(context).formatTimeOfDay(TimeOfDay(hour: ist.hour, minute: ist.minute)) + ' IST';
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Blind Date'), actions: [
        IconButton(tooltip: 'Refresh', onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh)),
      ]),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.all(AppSpacing.md), children: [
          const Text('Consent-first introductions', style: TextStyle(fontWeight: FontWeight.w800)),
          const SizedBox(height: AppSpacing.xs),
          const Text('Choose a 15-minute slot in the next 14 days. Pairing only happens when two eligible, unblocked people independently choose the same slot.'),
          const SizedBox(height: AppSpacing.md),
          Card(child: Padding(padding: const EdgeInsets.all(AppSpacing.md), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(_label(_slotAsUtcInstant()), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
            const SizedBox(height: AppSpacing.sm),
            Wrap(spacing: AppSpacing.xs, children: [
              OutlinedButton.icon(onPressed: _saving ? null : _chooseDate, icon: const Icon(Icons.calendar_month), label: const Text('Date')),
              OutlinedButton.icon(onPressed: _saving ? null : _chooseTime, icon: const Icon(Icons.schedule), label: const Text('Time')),
            ]),
            const SizedBox(height: AppSpacing.sm),
            SizedBox(width: double.infinity, child: FilledButton(onPressed: _saving ? null : _optIn,
              child: Text(_saving ? 'Saving…' : 'Opt in to this slot'))),
          ]))),
          const SizedBox(height: AppSpacing.lg),
          const Text('My Blind Date slots', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
          const SizedBox(height: AppSpacing.sm),
          if (_loading) const LinearProgressIndicator(),
          if (!_loading && _bookings.isEmpty) const Text('You have not opted into a slot yet.'),
          for (final booking in _bookings)
            Card(child: Padding(padding: const EdgeInsets.all(AppSpacing.md), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(_label(booking.startsAt), style: const TextStyle(fontWeight: FontWeight.w800)),
              const SizedBox(height: AppSpacing.xs),
              Text(booking.status == 'WAITING' ? 'Waiting for a partner' :
                booking.status == 'PAIRED' && booking.sessionStatus == 'SCHEDULED' ? 'Scheduled' :
                booking.sessionStatus == 'CANCELLED' ? 'Cancelled' : booking.status),
              if (booking.partnerProfilePublicId != null && booking.sessionStatus == 'SCHEDULED')
                Text('Partner profile ID: ' + booking.partnerProfilePublicId!, style: Theme.of(context).textTheme.bodySmall),
              if (booking.status == 'WAITING' ||
                  (booking.status == 'PAIRED' && booking.sessionStatus == 'SCHEDULED' && booking.startsAt.isAfter(DateTime.now())))
                Align(alignment: Alignment.centerRight, child: TextButton(onPressed: _saving ? null : () => _changeBooking(booking),
                  child: Text(booking.status == 'WAITING' ? 'Withdraw opt-in' : 'Cancel session'))),
            ]))),
          if (_error != null) Padding(padding: const EdgeInsets.only(top: AppSpacing.sm),
            child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error))),
          if (_notice != null) Padding(padding: const EdgeInsets.only(top: AppSpacing.sm), child: Text(_notice!)),
        ]),
      ),
    );
  }
}
