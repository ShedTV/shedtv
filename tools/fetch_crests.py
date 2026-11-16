"""Look up club crests on TheSportsDB (free key) and write app/src/main/assets/crests.json.

Run by the "Refresh crests" GitHub workflow. Spaced out to stay inside the free rate limit.
"""
import json
import time
import urllib.parse
import urllib.request
import urllib.error

BASE = "https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t="
OUT = "app/src/main/assets/crests.json"


def fetch(query):
    url = BASE + urllib.parse.quote(query)
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ShedTV crest builder", "X-API-KEY": "123"})
            with urllib.request.urlopen(req, timeout=30) as r:
                data = json.loads(r.read().decode("utf-8") or "{}")
                return data.get("teams") or []
        except urllib.error.HTTPError as e:
            print(f"::warning::{query}: HTTP {e.code} (attempt {attempt + 1})")
            time.sleep(30 * (attempt + 1))
        except Exception as e:  # network or JSON trouble
            print(f"::warning::{query}: {e} (attempt {attempt + 1})")
            time.sleep(10)
    return []


def pick(results, team):
    want_rugby = team["sport"] == "Rugby"

    def sport_ok(t):
        s = (t.get("strSport") or "")
        lg = (t.get("strLeague") or "")
        if want_rugby:
            return "Rugby" in s and ("League" in s or "League" in lg) and "Union" not in lg
        return s == "Soccer"

    ok = [t for t in results if sport_ok(t)]
    for t in ok:
        if (t.get("strTeam") or "").lower() in (team["lookup"].lower(), team["name"].lower()):
            return t
    return ok[0] if ok else None


def league_lists():
    """Whole-league listings (the free key returns up to 10 teams per league)."""
    out = []
    for lg in ["English Super League", "Super League", "English_Super_League", "English Premier League"]:
        url = "https://www.thesportsdb.com/api/v1/json/123/search_all_teams.php?l=" + urllib.parse.quote(lg)
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ShedTV crest builder", "X-API-KEY": "123"})
            with urllib.request.urlopen(req, timeout=30) as r:
                teams = json.loads(r.read().decode("utf-8") or "{}").get("teams") or []
            print(f"::notice::league '{lg}': " + ", ".join(f"{t.get('strTeam')}|{t.get('strSport')}" for t in teams))
            out += teams
        except Exception as e:
            print(f"::warning::league '{lg}': {e}")
        time.sleep(7)
    for tid in ["133720"]:  # Nottingham Forest
        url = "https://www.thesportsdb.com/api/v1/json/123/lookupteam.php?id=" + tid
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ShedTV crest builder", "X-API-KEY": "123"})
            with urllib.request.urlopen(req, timeout=30) as r:
                teams = json.loads(r.read().decode("utf-8") or "{}").get("teams") or []
            print(f"::notice::id {tid}: " + ", ".join(f"{t.get('strTeam')}|{t.get('strSport')}" for t in teams))
            out += teams
        except Exception as e:
            print(f"::warning::id {tid}: {e}")
        time.sleep(7)
    return out


def main():
    teams = json.load(open("tools/crest_teams.json"))
    try:
        crests = json.load(open(OUT))
    except Exception:
        crests = {}
    extra = None
    for team in teams:
        if team["key"] in crests:
            continue
        if extra is None:
            extra = league_lists()
        hit = pick([t for t in extra if (t.get("strTeam") or "").lower() in
                    [n.lower() for n in [team["lookup"], team["name"]] + team.get("alt", [])]], team)
        if hit and (hit.get("strBadge") or hit.get("strTeamBadge")):
            badge = hit.get("strBadge") or hit.get("strTeamBadge")
            crests[team["key"]] = badge if badge.endswith("/small") else badge + "/small"
            print(f"::notice::{team['key']}: {hit.get('strTeam')} | {hit.get('strSport')} | {hit.get('strLeague')} (league list)")
            continue
        queries = [team["lookup"], team["name"].split(" (")[0]] + team.get("alt", [])
        chosen = None
        for q in dict.fromkeys(queries):
            results = fetch(q)
            chosen = pick(results, team)
            if not chosen:
                seen = "; ".join(f"{t.get('strTeam')}|{t.get('strSport')}|{t.get('strLeague')}" for t in results[:6]) or "nothing"
                print(f"::warning::{team['key']} query '{q}' returned: {seen}")
            time.sleep(7)
            if chosen:
                break
        badge = chosen and (chosen.get("strBadge") or chosen.get("strTeamBadge"))
        if badge:
            crests[team["key"]] = badge if badge.endswith("/small") else badge + "/small"
            print(f"::notice::{team['key']}: {chosen.get('strTeam')} | {chosen.get('strSport')} | {chosen.get('strLeague')}")
        else:
            print(f"::warning::{team['key']}: no crest found")
    json.dump(crests, open(OUT, "w"), indent=1, sort_keys=True)
    print(f"::notice::crests found for {len(crests)} of {len(teams)} teams")


if __name__ == "__main__":
    main()
