DEEP INVESTIGATION: Spotify → Instagram Notes State Flow

Mission

We are NOT trying to build the final app yet.

We are trying to discover the minimum technical state that causes Instagram Notes to recognize a Spotify track.

The previous investigation successfully mapped much of Spotify's modern Web Player infrastructure, but it focused heavily on playback.

That is no longer the primary question.

The new question is:

«What exact Spotify-side state does Instagram depend on, and can that state exist without actual Spotify audio playback?»

The final desired architecture is:

YouTube Music / Obsidian Music
        ↓
Android MediaSession
        ↓
Title + Artist
        ↓
Spotify Track Resolution
        ↓
???? MINIMUM SPOTIFY STATE ????
        ↓
Instagram Backend
        ↓
Instagram Notes recognizes Spotify Track

The "????" is what we need to discover.

---

IMPORTANT PREVIOUS FINDINGS

Read the supplied "analysis.md" before beginning.

It established several things:

- Modern Spotify Web Player uses "/api/token" with TOTP.
- Pathfinder GraphQL can resolve Spotify tracks without Premium.
- Spotify track URIs can be obtained without Premium.
- Web Player / Connect-State playback uses Dealer + Spclient.
- Remote Connect playback is Premium-gated for Free accounts.
- An accepted Connect-State playback command can update Spotify's global playback state.
- The previous report claims that this state is reflected in "currently-playing" and social integrations such as Instagram Notes.

However:

CRITICAL

The claim that Instagram Notes directly depends on this global playback state has NOT been independently established strongly enough.

Therefore, do not treat it as fact.

The first objective is to experimentally verify or falsify it.

---

RULE ZERO

Do NOT spend the investigation trying to bypass:

- Spotify Premium restrictions
- DRM
- authentication
- CAPTCHA
- access controls
- account security
- paid features

If Spotify explicitly rejects an operation with:

premium_required

record that as a capability boundary.

Do NOT attempt to defeat it.

The research question is whether the desired Instagram behavior depends on that operation at all.

Use only accounts/devices we control.

---

PHASE 0 — VERIFY THE PREVIOUS REPORT

Before exploring new theories, reproduce the most important claims from "analysis.md".

Verify:

Test A

Spotify track:

spotify:track:<TEST_TRACK>

is resolved successfully without Premium.

Test B

Spotify's current playback state can be observed through the appropriate legitimate interface available to the test account.

Test C

Starting Spotify playback causes the expected Spotify-side state transition.

Test D

Determine whether that transition actually affects Instagram Notes.

Do NOT simply assume:

Spotify current playback
        ↓
Instagram Notes

Prove it.

---

PHASE 1 — BUILD A STATE OBSERVATION MATRIX

Use one controlled Spotify account and one controlled Instagram account.

Pick several known tracks.

For each experiment record:

- timestamp
- Spotify account
- Spotify track ID
- Spotify URI
- Spotify player state
- device state
- Connect state
- currently-playing state
- recently-played state
- relevant Spotify activity/presence
- Instagram-visible state
- Instagram network changes
- result

Create this matrix:

State| Spotify Track| Playing| Paused| Stopped| Recently Played| Connect State| Instagram Recognizes?
A| None| ❌| ❌| ❌| ❌| ❌| ?
B| Track X| ✅| ❌| ❌| ?| ?| ?
C| Track X| ❌| ✅| ❌| ?| ?| ?
D| Track X| ❌| ❌| ✅| ?| ?| ?
E| Track X| ❌| ❌| ❌| ✅| ?| ?
F| Track X| ❌| ❌| ❌| ?| ✅| ?

Do not infer missing values.

---

PHASE 2 — DETERMINE WHAT INSTAGRAM ACTUALLY OBSERVES

This is the most important phase.

Instrument the Instagram Android app using a controlled test environment.

Investigate the exact behavior when opening:

- Instagram Notes
- Add Note
- Music selection
- Spotify-related music UI
- any "currently listening" or music-related interface

Capture only traffic belonging to our controlled account/session.

Look for:

- GraphQL operations
- REST APIs
- provider identifiers
- Spotify IDs
- Spotify URIs
- track IDs
- current playback fields
- recently played fields
- activity fields
- presence fields
- linked music provider information
- external metadata references

For every potentially relevant request, determine:

REQUEST
↓
What triggered it?
↓
What data does it contain?
↓
Does changing Spotify state change it?
↓
Does changing the Spotify track change it?
↓
Does removing Spotify activity change it?

Do not label an endpoint "the Spotify endpoint" without controlled evidence.

---

PHASE 3 — TEST THE DIRECTION OF INFORMATION FLOW

We need to establish whether the architecture is:

Model A

Spotify
   ↓
Instagram queries Spotify

or:

Model B

Spotify
   ↓
Spotify backend
   ↓
Instagram backend
   ↓
Instagram app

or:

Model C

Spotify
   ↓
Instagram-linked account state
   ↓
Instagram app

or:

Model D

Spotify track metadata
   ↓
Instagram has independent metadata mapping

or another model.

For each model, identify actual evidence.

---

PHASE 4 — CURRENT PLAYBACK VS RECENTLY PLAYED

This distinction is critical.

Run controlled experiments.

Experiment 1

Start Track X.

Verify Spotify current state.

Immediately inspect Instagram.

Experiment 2

Start Track X.

Pause it.

Inspect Instagram.

Experiment 3

Start Track X.

Stop it completely.

Inspect Instagram.

Experiment 4

Start Track X.

Switch to Track Y.

Inspect Instagram.

Determine whether Instagram follows:

X → Y

or remains:

X

or clears completely.

Experiment 5

Play X.

Close Spotify.

Inspect Instagram.

Experiment 6

Play X.

Wait a controlled period.

Inspect Instagram.

Experiment 7

Play X.

Then play something from YouTube Music instead.

Inspect Instagram.

This tells us whether Instagram cares about Spotify's current state or merely a persistent/cached state.

---

PHASE 5 — TEST WHETHER AUDIO IS ACTUALLY NECESSARY

We are specifically investigating whether:

Spotify track identity
+
Spotify player state

is sufficient without:

actual audio output

Possible states to test, only where normally supported:

A

Spotify reports Track X as current and is playing.

B

Spotify reports Track X as current but playback is paused.

C

Spotify has Track X as the latest playback state but audio is no longer active.

D

Spotify device remains registered with Track X as its state.

E

Track X is only in recently-played history.

Compare Instagram behavior.

The question is:

«At what point does Instagram stop recognizing X?»

That boundary is more valuable than trying random playback hacks.

---

PHASE 6 — IDENTIFY THE MINIMUM STATE VECTOR

From all experiments, construct a candidate state vector.

For example:

{
  "spotifyTrackId": "...",
  "isPlaying": true,
  "deviceActive": true,
  "playerState": "...",
  "recentlyPlayed": true,
  "connectState": true
}

Then progressively remove fields conceptually.

Determine the minimum set:

{
  "spotifyTrackId": "..."
}

or:

{
  "spotifyTrackId": "...",
  "playerState": "PLAYING"
}

or:

{
  "spotifyTrackId": "...",
  "deviceState": "ACTIVE"
}

etc.

Do NOT claim a minimum state unless experiments support it.

---

PHASE 7 — INVESTIGATE SPOTIFY PROTOCOLS

Only after understanding Instagram's dependency, investigate Spotify protocols relevant to producing that state.

Research current behavior of:

- Web Player
- Connect-State
- Dealer
- Spirc
- Mercury
- librespot
- librespot-java
- spclient
- player state
- device state
- account activity
- current playback
- recently played

For each capability determine:

Operation| Free| Premium| Requires Audio| Requires Device| Relevant to Instagram?
Search| ?| ?| ❌| ❌| ?
Track resolution| ?| ?| ❌| ❌| ?
Current playback| ?| ?| ?| ?| ?
Player state| ?| ?| ?| ?| ?
Device state| ?| ?| ?| ?| ?
Connect state| ?| ?| ?| ?| ?
Recently played| ?| ?| ❌| ?| ?
Social/activity state| ?| ?| ?| ?| ?

Clearly distinguish:

- publicly documented
- experimentally observed
- open-source implementation
- historical
- inferred
- unknown

---

PHASE 8 — INVESTIGATE SPIRC / MERCURY

This is a research phase, NOT a Premium bypass.

Determine whether modern Spotify still permits a legitimate client to:

1. Authenticate.
2. Establish a Spotify client session.
3. Register a device.
4. Report player state.
5. Report a track.
6. Change account playback state.
7. Propagate that state globally.
8. Do any of the above on Free accounts.

Use current open-source implementations as references.

Do not assume behavior from old librespot versions is still valid.

If authentication or playback is Premium-only, document exactly where that restriction appears.

---

PHASE 9 — SOCIAL STATE EXPERIMENT

This is the experiment I care about most.

We want to determine whether:

Spotify global playback state

and:

Instagram Notes Spotify recognition

are actually correlated.

Perform:

Track X
↓
Spotify state changes
↓
Observe Spotify
↓
Observe Instagram

Then:

Track Y
↓
Spotify state changes
↓
Observe Spotify
↓
Observe Instagram

Then compare the exact Spotify IDs observed by Instagram.

If Instagram changes from X → Y exactly when Spotify changes X → Y, document the evidence.

If Instagram does NOT change, the previous report's claim about social integration must be marked unverified/incorrect.

---

PHASE 10 — CACHE / DELAY ANALYSIS

Determine whether Instagram is:

- real-time
- delayed
- periodically synchronized
- cached
- event-driven

Test:

T0 = Spotify changes track
T1 = Instagram first detects change

Measure T1 - T0.

Repeat multiple times.

If Instagram only updates after opening/reloading a screen, document that.

This may reveal that Instagram queries a persistent backend state rather than receiving a live event.

---

PHASE 11 — SOURCE-SIDE IMPLEMENTATION

Do NOT build the complete app.

Only verify:

YouTube Music / Obsidian
        ↓
Android MediaSession
        ↓
Title
Artist
Album
        ↓
Spotify Track Resolver
        ↓
Spotify Track URI

The resolver should be independent of Spotify playback.

For example:

"Blinding Lights"
"The Weeknd"
        ↓
spotify:track:0VjIjW4GlUZAMYd2vXMi3b

Test matching quality with multiple tracks.

---

PHASE 12 — DO NOT CONFUSE THESE TWO PROBLEMS

Keep these separate:

Problem A

How do we identify the Spotify track?

This is already largely solved.

Problem B

How do we make Instagram recognize that Spotify track?

This is the unresolved problem.

Do not let successful Spotify search/track resolution be mistaken for solving Problem B.

---

PHASE 13 — SECURITY BOUNDARIES

Do NOT:

- steal credentials
- extract another person's cookies
- bypass authentication
- bypass Premium restrictions
- bypass DRM
- defeat CAPTCHA
- exploit Spotify/Instagram infrastructure
- modify server-side data without authorization
- impersonate another account

Testing with our own account and normal authenticated access is allowed.

If the only route requires bypassing an access control, mark it:

BLOCKED BY ACCESS CONTROL

and move on.

---

FINAL DELIVERABLE

Produce a detailed report with these sections:

1. VERIFIED END-TO-END FLOW

Show the experimentally confirmed flow.

2. INSTAGRAM DEPENDENCY

Answer:

«What exact Spotify state does Instagram depend on?»

3. MINIMUM STATE

Answer:

«What is the minimum state required for Instagram to recognize Track X?»

4. DOES AUDIO MATTER?

Answer:

YES
NO
UNKNOWN

and explain the evidence.

5. DOES PREMIUM MATTER?

Separate:

Spotify playback Premium requirement

from:

Instagram recognition requirement

Do not automatically treat them as the same thing.

6. FREE ACCOUNT CAPABILITIES

Provide the tested capability matrix.

7. SPOTIFY PROTOCOL FINDINGS

Include Web Player / Connect / Dealer / Spirc / Mercury findings.

8. INSTAGRAM NETWORK FINDINGS

List only experimentally relevant requests.

For each:

- endpoint
- operation
- trigger
- important fields
- relationship to Spotify state
- confidence

9. MINIMUM REPRODUCIBLE STATE

Give the smallest experimentally supported state representation.

10. FEASIBILITY

Choose exactly one:

🟢 FEASIBLE

The required state can be produced without Spotify app visibility and without Premium.

🟡 PARTIALLY FEASIBLE

The core mechanism is understood, but one specific technical dependency remains.

🔴 NOT FEASIBLE

A mandatory dependency requires something forbidden by the requirements.

⚪ UNKNOWN

The evidence is insufficient and a specific experiment is still required.

11. NEXT EXPERIMENT

If not 🟢, specify the SINGLE highest-value next experiment.

Do not give generic suggestions.

---

FINAL INSTRUCTION

Think like a protocol researcher, not an application developer.

Do not stop at:

«"Spotify requires Premium."»

Instead answer:

«"Premium is required for operation X. Instagram requires operation Y. X and Y are / are not equivalent because..."»

That distinction is the entire purpose of this investigation.

Do not build production code until the state dependency is experimentally understood.