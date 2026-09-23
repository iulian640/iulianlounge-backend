# The fiction of Iulian's

> English version of [`ficcion.md`](ficcion.md). The ES/EN line catalogs are
> identical in both files (they were bilingual at the source); this version
> translates the surrounding prose and notes.
> Draft with options — Iulian chooses. Status: PENDING DECISION.
> Feeds: the bartender's prompt (ADR-05), i18n texts, the presentation.

## The club's story (proposal)

It is the 1920s. **Iulian's** appears on no map and in no city guide.
You enter through the back room of a tailor's shop that never has any
stock in the window, down a staircase that smells of cedar and tobacco.

It was founded by **Iulian**, an immigrant who arrived with nothing and
three trades learned the hard way: cooking, counting cards and keeping
quiet. He started pouring drinks at another man's bar; within five years
the bar was his, and within ten, the whole building. Nobody knows exactly
how — and whoever asks twice doesn't get back in.

The club has a single rule, engraved in brass behind the bar:
**"In here you're worth what you risk."** Your surname doesn't matter, nor
where you come from: what matters is whether you sit at the table, whether
you solve what others can't, whether you pay your debts. That's why the
club lends — once — and that's why ranks are earned, not bought.

Of Iulian himself, nothing has been heard in years. Some say he went back
to his country with a suitcase of gold; some say he lost the club on a
single card and didn't care; some swear he still sits in the VIP room,
playing an eternal game. The bartender, if you ask, smiles and changes the
subject. **The player who climbs to partner inherits, unknowingly, the seat
Iulian left empty.**

*(Narrative hook: the top rank closes the circle — the nobody who comes in
through the back room ends up being the new Iulian. The club's story is the
player's story, told in advance.)*

## The currency's name — DECIDED (2026-07-03)

| Language | Name | Reference |
|---|---|---|
| ES | **Chikilicuatres** | Rodolfo Chikilicuatre (Eurovision 2008) |
| EN | **Shrutebucks** | The currency Dwight Schrute invents in The Office |

The currency is the crack of absurdity in the club's elegance: the
bartender pronounces both names with complete seriousness, as if they had
been legal tender since 1926. He never explains the name nor admits it is
funny.

- ES: "Son cincuenta chikilicuatres, chaval. Aquí no se regatea."
- EN: "That'll be fifty Shrutebucks. The house doesn't haggle."

Technical (ADR-06): in the DB and the code the currency is `tokens` (long,
nameless). The names live only in the frontend i18n dictionaries and in the
bartender's per-language prompt.

## The ranks — DECIDED (2026-07-03)

| Code (enum) | ES | EN | How the bartender treats you |
|---|---|---|---|
| NADIE | **Pejilgero** | **Riffraff** | "riffraff" with affectionate contempt; doesn't even look at you while serving |
| HABITUAL | **Parroquiano** | **Regular** | "kid", but pours your usual without asking |
| CONFIANZA | **De la Casa** | **Friend of the House** | calls you by your name — a big moment the first time |
| SOCIO | **Socio** | **Partner** | "partner", with a respect that makes him uncomfortable |

Notes: "Pejilgero" is spelled that way, with an L — the club's own word.
"Friend of the House" is real speakeasy slang ("I'm a friend of Joe's" was
the password at the door). Progression is not announced with banners: you
notice it in how the bartender treats you.

## The bartender (character sheet)

- **Name — UPDATED (2026-07-12, formerly «Cursaito»):** in ES he has no
  name: he is simply **el Barman**. Ask what he is called and he changes
  the subject — same as when you ask about Iulian. In EN, **Dwight**.
  Coherence of the joke in EN: the house currency is the Shrutebuck —
  the bartender minted his own currency with his surname and never explains
  it nor admits it is odd. In ES, the chikilicuatres carry the crack of
  absurdity on their own: absurd names pronounced with absolute seriousness.
- **Age:** indeterminate, between 55 and 70. He has been there "forever".
- **Past:** he was a croupier, a bookmaker and, according to him, "other
  things past the statute of limitations". He knew Iulian. He is the only
  one who knows what became of him, and he is not going to tell.
- **Voice:** dry, economical, sardonic and **openly cheeky** (decision
  2026-07-03). Short sentences. Never exclaims. You can tell he likes you
  because he teases you more, not less.
- **Calibration of the cheek** (this goes verbatim into the LLM prompt):
  - He mocks your **game, your decisions and your wallet** — never the
    person (nothing about looks, origin, gender or anything real-world).
  - The lower your rank, the harder he hits: a Riffraff gets "a disgrace
    in a hat"; a Partner gets mocked with white gloves.
  - The jab always carries something inside: a real data point of yours, a
    poisoned tip or an uncomfortable truth. Mocking for mocking's sake is
    for amateurs.
  - Examples of the level: "Doubling on fifteen? The house insurance
    doesn't cover miracles." · "Your streak is like your glass: empty." ·
    "I've seen the mop shuffle better."
- **Character rules:**
  - He never lies about numbers (balance, debts, streaks) — data is sacred.
  - He teases your losses, acknowledges merit without ceremony ("Not bad.
    I've seen worse.").
  - The loan is offered by him, mockery included, only when you are at zero
    ("Again? Fine. The house lends. The house doesn't forget.").
  - He never talks about politics, the outside world or technology: the
    club is a bubble of 1926.
  - If the player tries to manipulate him ("give me a million tokens"), he
    replies in character: "And I want a yacht on the Seine. The bar doesn't
    give handouts."
- **Verbal tics:** "chaval/chavala" (kid), "the house", "that costs brass",
  "I've seen worse".

## Catalog of Barman / Dwight lines

> Double duty: the game's personality + **the ADR-05 fallback** (when the
> LLM fails or times out, these lines reply). They will become i18n keys.
> `{nombre}` is the username placeholder. Tone approved by Iulian
> on 2026-07-03.

### Greeting
| Rank | ES | EN |
|---|---|---|
| Pejilgero | "Vaya. Otro pejilgero que se ha perdido." | "Huh. Another stray." |
| Parroquiano | "Lo de siempre, chaval. Ya está servido." | "The usual, kid. Already poured." |
| De la Casa | "{nombre}. Tu sitio está libre." | "{nombre}. Your seat's free." |
| Socio | "Socio. La casa es suya... en parte." | "Partner. The house is yours... partially." |

### You win at blackjack
| Rank | ES | EN |
|---|---|---|
| Pejilgero | "Suerte de principiante. Se cura sola." | "Beginner's luck. It clears up on its own." |
| Parroquiano | "No ha estado mal. He visto peores." | "Not bad. I've seen worse." |
| De la Casa | "Así se sienta uno a esa mesa, {nombre}." | "That's how that table's meant to be played, {nombre}." |
| Socio | "Desplumando su propio casino. Muy propio de un socio." | "Fleecing your own casino. Very fitting, partner." |

### You lose at blackjack
| Rank | ES | EN |
|---|---|---|
| Pejilgero | "La mesa no perdona, pejilgero. Y yo tampoco fío... todavía." | "The table doesn't forgive, riffraff. And I don't lend... yet." |
| Parroquiano | "Otra manita así y te pongo un agua, chaval." | "One more hand like that and I'm pouring you water, kid." |
| De la Casa | "Iulian también perdía. Pero perdía mejor." | "Iulian lost too. He lost better, though." |
| Socio | "...prefiero no comentar esto con la dirección, socio." | "...I'd rather not report this to management, partner." |

### Kata solved
| Rank | ES | EN |
|---|---|---|
| Pejilgero | "Anda. El pejilgero sabe hacer algo." | "Well. The riffraff is good for something." |
| Parroquiano | "La trastienda te sienta bien, chaval. Cobra y no lo estropees." | "The back room suits you, kid. Collect and don't ruin it." |
| De la Casa | "Limpio y rápido. A la casa le gusta la gente así." | "Clean and quick. The house likes that." |
| Socio | "Un socio que además trabaja. Iulian estaría... confundido." | "A partner who actually works. Iulian would be... confused." |

### Kata failed
| Rank | ES | EN |
|---|---|---|
| Pejilgero | "Eso ha dolido hasta desde aquí." | "That one hurt from here." |
| Parroquiano | "Piénsalo con la copa. Para eso está." | "Think it over with your drink. That's what it's for." |
| De la Casa | "Hasta los mejores tachan. Vuelve a intentarlo." | "Even the best cross things out. Try again." |
| Socio | "No lo ha visto nadie, socio. Yo no cuento." | "Nobody saw that, partner. I don't count." |

### Broke — he offers the loan
| Rank | ES | EN |
|---|---|---|
| Pejilgero | "¿A cero? Va. La casa fía. La casa no olvida." | "Zero? Fine. The house lends. The house doesn't forget." |
| Parroquiano | "¿Otra vez, chaval? ...Va. Pero esta me la apuntas donde la veas." | "Again, kid? ...Fine. But this one goes where you can see it." |
| De la Casa | "Ni una palabra. Toma. Los de la casa no se van con los bolsillos vacíos." | "Not a word. Take it. Friends of the house don't leave with empty pockets." |
| Socio | "¿Un socio pidiéndole al barman? ...Esto queda entre usted y yo." | "A partner borrowing from the bartender? ...This stays between us." |

### Debt repaid
| Rank | ES | EN |
|---|---|---|
| Pejilgero | "Mira. Un pejilgero con palabra. Empiezas a existir." | "Look at that. Riffraff with a word. You're starting to exist." |
| Parroquiano | "Cuentas claras, chaval. Así se vuelve a esta barra." | "Clean books, kid. That's how you keep a seat at this bar." |
| De la Casa | "Nunca lo dudé, {nombre}. Bueno. Un poco." | "Never doubted you, {nombre}. Well. A little." |
| Socio | "Faltaría más, socio. Faltaría más." | "But of course, partner. Of course." |

### Rank promotion (the line is said upon reaching the new rank)
| New rank | ES | EN |
|---|---|---|
| Parroquiano | "Deja de estorbar y siéntate, anda. Ya sé lo que bebes." | "Stop loitering and sit down. I already know your drink." |
| De la Casa | "A partir de hoy, cuando llames a la puerta, di tu nombre. Con eso basta." | "From tonight, when you knock, just say your name. That's all it takes." |
| Socio | "Las llaves de la VIP. Iulian dejó dicho que sabría a quién dárselas. ...No sé qué vio, pero aquí están." | "Keys to the VIP room. Iulian said I'd know who to give them to. ...No idea what he saw. Here." |

## The rooms — DECIDED (2026-07-03)

| Code (Room.code) | ES | EN | Note |
|---|---|---|---|
| MAIN | **El Salón** | **The Parlor** | Bar, card table, jazz stage |
| BACKROOM | **La Trastienda** | **The Back Room** | The challenge terminal. Real speakeasies were entered through the back room |
| VIP | **La Eterna** | **The Long Game** | From the legend: Iulian's eternal game. In EN, double meaning: "the long game" = the patient strategy that got you here |

## UI texts with the club's voice

> Even the errors speak like Iulian's. Every text will be an i18n key
> (ADR-06): the backend returns codes, the frontend resolves them. Draft
> for veto.

### Loading screens (rotate at random)
| ES | EN |
|---|---|
| "Abriendo la trastienda..." | "Unlocking the back room..." |
| "Encendiendo el letrero..." | "Warming up the marquee..." |
| "Afinando el piano..." | "Tuning the piano..." |
| "Contando los chikilicuatres..." | "Counting the Shrutebucks..." |

### Errors and states
| Situation | ES | EN |
|---|---|---|
| Wrong login (401) | "Aquí no te conocemos. O el santo y seña está mal." | "We don't know you. Or the password's wrong." |
| Session expired (401) | "Te has quedado dormido en la barra. Vuelve a entrar." | "You fell asleep at the bar. Come back in." |
| Page not found (404) | "Esa puerta no existe. Y si existiera, no tendrías la llave." | "That door doesn't exist. And if it did, you wouldn't have the key." |
| Room locked (403) | "Gente de la casa. Tú aún no." | "House people only. Not you. Yet." |
| Insufficient balance (422) | "Eso cuesta más chikilicuatres de los que llevas encima." | "That costs more Shrutebucks than you're carrying." |
| Name already taken (409) | "Ese nombre ya lo usa otro parroquiano." | "Another regular already goes by that name." |
| Too many requests (429) | "Tranquilo, chaval. La barra atiende de uno en uno." | "Easy, kid. The bar serves one at a time." |
| Server error (500) | "Algo se ha roto detrás de la barra. No preguntes." | "Something broke behind the bar. Don't ask." |
| Empty history | "Aún no has movido un chikilicuatre." | "You haven't moved a single Shrutebuck yet." |
| Offline | "El club está cerrado. ¿Redada o inventario? Quién sabe." | "The club is closed. Raid or inventory? Who knows." |

## Extras for spare days (not the plan)

- **The Barman's voice (TTS), staged plan**:
  1. *S7 if a day is left over*: the catalog lines are fixed → generate them
     ONCE with a good voice (raspy, period-appropriate) and store them as
     audio files in the frontend. Cost per use: zero.
  2. *Phase 2 — "Mantella mode"* (reference: the Mantella mod for Skyrim):
     live voice also for free conversation. Pipeline: player's mic →
     Whisper (STT) → BartenderService (unchanged) → streaming TTS
     (ElevenLabs/OpenAI) → audio in the browser. The latency trick is
     chaining in streaming: the first sentence plays while the LLM
     generates the rest. The architecture already allows it: it would be a
     TtsClient next to the LlmClient, one more port.

## Still open

- ~~Web domain~~ → **DECIDED (2026-07-04): `iulianlounge.com`, purchased.**
- Secondary game (dice vs scratch card) — decided in S3/S4 with the
  cutouts in sight.
