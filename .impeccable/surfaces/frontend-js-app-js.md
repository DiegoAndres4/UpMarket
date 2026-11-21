---
version: 1
slug: "frontend-js-app-js"
primary_target: "frontend/js/app.js"
related_targets: ["frontend/css/styles.css"]
---

# Surface: public landing (`#/` without session)

Scope: the informative landing visitors without a session land on. Visitor mode: Persuade. Audience: UPMarket students who
want to buy and students who want to sell, equally. Action: search the board (working form) and create an account.
Proof: real latest paid publications from a public read-only endpoint. Constraints: incumbent tokens and Archivo face,
no invented numbers or testimonials, no contact data on public listings.

## Direction contract

THESIS: The landing IS the campus notice board (cartelera de la sede): real offers pinned in one place, ordered by
category, staying until sold. It refuses the category default of a centred headline over three feature cards and a
phone mockup.

OWN-WORLD: the incumbent UPMarket world at full commitment: the brand yellow `--primary` as a whole field that is the
board itself, white paper notices with a red `--accent` price tab and a red pin dot, ink `--text`, Archivo set large and
tight. Surfaces separate by tone, never by hairlines or resting shadows.

STORY: in five seconds the visitor reads "this is the board of my campus", sees real things for sale with prices,
filters them by category or keyword right there, understands that buying is free and publishing costs $3.000 with
view counts, and creates an account.

FIRST VIEWPORT: plain top bar (wordmark left; "Ingresar" link and "Crear cuenta" button right). Left-aligned h1 "La
cartelera de la sede." at display size with one supporting sentence directly beneath, then the working search field
with category chips. The yellow board field starts inside the first viewport, its first row of real notices visible
above the fold on desktop.

FORM: candidate 4 of 7 (campus notice board), dealt lead. seed:ddd2950be6471cd9

SIGNATURE INTERACTION: choosing a category chip or typing a search re-pins the board in place from the public
endpoint: notices lift away and the new ones pin in with a short staggered drop; an aria-live count announces the result.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
