# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Students of the Universidad de Pamplona, Villa del Rosario campus, in two equal roles: the student who wants to find
something (a used item, a book, a classmate's small business) and the student who wants to sell it. Both are on their
phones between classes. An administrator (role `ADMIN`) moderates publications, categories and users.

## Product Purpose

UPMarket centralizes the buying and selling that today happens in campus WhatsApp groups, where offers mix with
unrelated messages, have no categories or filters, and scroll away within hours. A student publishes an item (title,
description, price, new/used, category, up to 5 photos, contact), another finds it by keyword, category or filters,
opens the detail and contacts the seller. The purchase is agreed outside the platform.

Success (project hypothesis): a student finds a specific offer faster than in the WhatsApp groups, and at least 70% of
pilot participants rate the organization of offers as better than today.

## Positioning

The marketplace of one campus: only students of this university and this sede, organized by category instead of lost
in a chat. Neighboring products (WhatsApp groups, Facebook Marketplace) cannot truthfully claim "everything here is
from your own campus, findable by category, and stays until it is sold".

## Business model (confirmed facts)

- Publishing costs 3.000 COP per publication. In this version the payment is SIMULATED (no real money).
- A new publication stays hidden until paid.
- Sellers see how many people viewed each publication; only the owner and the admin see the count.
- Contact and payment between students happen outside the platform; UPMarket processes no purchases or deliveries.

## Assets and brand commitments

- Wordmark "UP" + "Market" (existing header style), university shield (`frontend/public/escudo-128.png`,
  `escudounipamplona.png`), project mascot (`frontend/public/mascota-upmarket.png`).
- Existing tokens in `frontend/css/styles.css` (yellow primary, red accent, Archivo typeface) are the incumbent world.
- It is a Proyecto Integrador of the Universidad de Pamplona.

## Evidence

No pilot results, user counts or testimonials exist yet. Never invent them. Real publications can be shown through a
public read-only endpoint (latest paid publications, without contact data).

## Open decisions

- Support contact email (`SOPORTE_CORREO`) is not defined yet.
- Email verification before login depends on a pending decision about modifying AuthCore.
