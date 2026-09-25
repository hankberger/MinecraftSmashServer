"""Real-container points delivery, with disposable identities in the empty CI network only."""
import argparse
import json
import time
import uuid
from manage import Admin, ROOT


def check_saved_accounts():
    state = ROOT / 'build/points-check.json'
    if not state.exists():
        return
    lobby = Admin('http://127.0.0.1:18083', ROOT / 'deploy/secrets/control')
    saved = json.loads(state.read_text())
    for player, expected in saved['accounts'].items():
        actual = lobby.call('/test/points', {'action': 'status', 'player': player})
        assert actual['account'] == expected, (player, actual, expected)
        if player in saved.get('wardrobes', {}):
            assert actual['wardrobe'] == saved['wardrobes'][player], (player, actual)
    if 'economy' in saved:
        assert Admin().call('/economy') == saved['economy']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True)
    args = parser.parse_args()
    if args.project != 'smash-ci':
        parser.error('This fixture only runs in the disposable smash-ci project.')
    state = Admin().call()
    assert state['protocol'] == 6 and all(not n['status']['players'] for n in state['nodes'].values())
    lobby = Admin('http://127.0.0.1:18083', ROOT / 'deploy/secrets/control')
    arenas = [Admin('http://127.0.0.1:' + str(port), ROOT / 'deploy/secrets/control') for port in (18081, 18082)]
    players = [str(uuid.uuid4()) for _ in range(2)]

    def result(winner, ticks=6000, quit=None):
        roster = []
        for player in players:
            selection = str(uuid.uuid4())
            roster.append(dict(player=player, fighter='STEVE', mode='DUEL', selection=selection, group=selection, groupSize=1))
        return dict(id=str(uuid.uuid4()), mode='DUEL', winner=winner, roster=roster, rows=[dict(player=player,
            name='PointsFixture', fighter='STEVE', slot=i+1, stocks=1 if player==winner else 0,
            knockouts=1, falls=1, damage=100, forfeited=player==quit) for i, player in enumerate(players)],
            evidence=dict(roundTicks=ticks, players={p: dict(playedTicks=ticks, activeTicks=min(ticks, 3000)) for p in players}))

    def wait_paid(amount):
        for _ in range(80):
            accounts = {p: lobby.call('/test/points', {'action': 'status', 'player': p})['account'] for p in players}
            empty = all(not a.call('/test/points', {'action': 'status', 'player': players[0]})['pending'] for a in arenas)
            if [accounts[p]['balance'] for p in players] == amount and empty:
                return accounts
            time.sleep(.25)
        raise AssertionError(('Points delivery did not finish', accounts))

    first = result(players[0])
    arenas[0].call('/test/points', {'action': 'record', 'result': first})
    wait_paid([75, 50])
    # Retransmit both from the original worker and from another worker.
    for arena in arenas:
        arena.call('/test/points', {'action': 'record', 'result': first})
    wait_paid([75, 50])
    arenas[1].call('/test/points', {'action': 'record', 'result': result(players[1])})
    accounts = wait_paid([125, 125])
    assert all(a == dict(balance=125, earned=125, matches=2, wins=1) for a in accounts.values())
    for worker in arenas:
        worker.call('/test/points', {'action': 'record', 'result': result(players[0])})
    wait_paid([275, 225])
    for i in range(10):
        arenas[i % 2].call('/test/points', {'action': 'record', 'result': result(players[0])})
    wait_paid([1025, 725])
    arenas[0].call('/test/points', {'action': 'record', 'result': result(players[0], ticks=5, quit=players[1])})
    wait_paid([1025, 725])
    purchase = dict(player=players[0], fighter='STEVE', skin='diamond', purchase=True)
    assert lobby.call('/test/outfit', purchase)['result'] == 'PURCHASED'
    assert lobby.call('/test/outfit', purchase)['result'] == 'EQUIPPED'
    assert lobby.call('/test/outfit', {**purchase, 'player': players[1]})['result'] == 'NEED_POINTS'
    states = {p: lobby.call('/test/points', {'action': 'status', 'player': p}) for p in players}
    assert states[players[0]]['account'] == dict(balance=275, earned=1025, matches=14, wins=13)
    assert states[players[0]]['wardrobe'] == dict(owned=['diamond'], equipped={'STEVE': 'diamond'})
    economy = Admin().call('/economy')
    assert economy['prices'] == dict(standardSkin=1500, firstSkin=750, elaborateSkin=3000, futureClass=5000)
    assert economy['measuredRounds'] == 15 and economy['measuredFirstPurchases'] == 1
    assert economy['rewardOutcomes']['TOO_SHORT'] == 1 and economy['rewardOutcomes']['LEFT_EARLY'] == 1
    (ROOT / 'build/points-check.json').write_text(json.dumps({'passed': True,
        'accounts': {p: s['account'] for p, s in states.items()}, 'wardrobes': {p: s['wardrobe'] for p, s in states.items()}, 'economy': economy}, indent=2))
    print('POINTS_NETWORK_CHECK_PASSED: both workers paid once; first skin discounted once, short quit unpaid, insufficient funds rejected, economy report saved.')


if __name__ == '__main__':
    main()
