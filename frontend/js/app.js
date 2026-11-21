/* UPMarket · frontend sin dependencias ni paso de compilación.
 * Todo se llama por rutas relativas (/api/...): nginx reparte /api/auth a AuthCore y el resto al servicio de dominio.
 * El texto de los usuarios se inserta SIEMPRE con textContent (nunca innerHTML), así no hay XSS. */
'use strict';

const API = '';           // mismo origen. Para desarrollo local se puede cambiar (ver README).
const CLAVE_TOKEN = 'upmarket.token';
const CLAVE_PERFIL_PENDIENTE = 'upmarket.perfilPendiente';
const SOPORTE_CORREO = '';  // correo de soporte; si está vacío, no se muestra "Contactar soporte" en el pie
let perfilActual = null;

/* ---------- utilidades ---------- */
function h(etiqueta, atributos, ...hijos) {
  const el = document.createElement(etiqueta);
  for (const [k, v] of Object.entries(atributos || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'value') el.value = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const hijo of hijos.flat()) {
    if (hijo === null || hijo === undefined || hijo === false) continue;
    el.append(hijo.nodeType ? hijo : document.createTextNode(String(hijo)));
  }
  return el;
}
const $app = () => document.getElementById('app');
function montar(...nodos) {
  const app = $app();
  app.replaceChildren(...nodos.flat());
  window.scrollTo(0, 0);
  const titulo = app.querySelector('h1');
  if (titulo) { titulo.setAttribute('tabindex', '-1'); titulo.focus({ preventScroll: true }); }
}
function aviso(texto, mal) {
  const a = document.getElementById('aviso');
  a.textContent = texto; a.className = 'aviso' + (mal ? ' mal' : ''); a.hidden = false;
  a.setAttribute('role', mal ? 'alert' : 'status');
  a.setAttribute('aria-live', mal ? 'assertive' : 'polite');
  clearTimeout(aviso.t); aviso.t = setTimeout(() => { a.hidden = true; }, 4500);
}
const pesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const precio = (n) => pesos.format(Number(n));
const fecha = (iso) => new Date(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' });
const vistasTexto = (n) => Number(n) + (Number(n) === 1 ? ' vista' : ' vistas');

/* Iconos SVG de trazo único: nada de emoji como interfaz. */
function iconoSvg(d, tamano) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  for (const [k, v] of Object.entries({ viewBox: '0 0 24 24', width: String(tamano || 16),
    height: String(tamano || 16), fill: 'none', stroke: 'currentColor', 'stroke-width': '1.8',
    'stroke-linecap': 'round', 'stroke-linejoin': 'round', class: 'icono', 'aria-hidden': 'true',
    focusable: 'false' })) svg.setAttribute(k, v);
  const path = document.createElementNS(ns, 'path');
  path.setAttribute('d', d);
  svg.append(path);
  return svg;
}
const iconoVista = () => iconoSvg('M2.5 12S6 6.5 12 6.5 21.5 12 21.5 12 18 17.5 12 17.5 2.5 12 2.5 12zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z', 16);
function iconoEstrella(llena) {
  const svg = iconoSvg('M12 3.6l2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8L3.6 9.7l5.8-.8z', 16);
  if (llena) { svg.setAttribute('fill', 'currentColor'); svg.setAttribute('stroke', 'currentColor'); }
  return svg;
}
function indicador(texto, tipo, icono) {
  return h('span', { class: 'indicador' + (tipo ? ' indicador--' + tipo : '') },
    icono || h('span', { class: 'indicador__punto', 'aria-hidden': 'true' }), texto);
}
function lineaIndicadores(items) {
  const nodos = [];
  items.filter(Boolean).forEach((item, i) => {
    if (i) nodos.push(h('span', { class: 'separador', 'aria-hidden': 'true' }, ' · '));
    nodos.push(item);
  });
  return h('span', { class: 'linea-indicadores' }, nodos);
}
/* Estado de una publicación con el mismo lenguaje visual en catálogo, detalle y Mis publicaciones. */
function indicadoresPublicacion(p, opciones) {
  const o = opciones || {};
  const items = [];
  if (o.estado !== false) {
    items.push(p.estadoPublicacion === 'VENDIDA'
      ? indicador('Vendido', 'vendido')
      : indicador('Disponible', 'disponible'));
  }
  if (o.articulo) items.push(indicador(p.estadoArticulo === 'USADO' ? 'Usado' : 'Nuevo', 'neutro'));
  if (o.categoria) items.push(h('span', { class: 'indicador indicador--neutro' }, p.categoria));
  if (p.vistas != null) items.push(indicador(vistasTexto(p.vistas), 'neutro', iconoVista()));
  if (o.pago) {
    if (p.estadoPago === 'PENDIENTE') items.push(indicador('Pendiente de pago', 'alerta'));
    else if (p.estadoPago === 'APROBADO') items.push(indicador('Pagado', 'ok'));
  }
  return lineaIndicadores(items);
}

/* Validaciones (el servidor vuelve a validar todo; esto solo da el aviso antes). */
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const MSG_CEL = 'Escribe los 10 dígitos de tu celular; debe empezar por 3 (ej. 300 123 4567)';
const limpiarTel = (t) => (t || '').replace(/[\s-]/g, '');
/* El +57 va fijo en pantalla: la persona escribe solo sus 10 dígitos (si pega +57…, se lo quitamos). */
const soloDigitos = (t) => { let d = String(t || '').replace(/\D/g, ''); if (d.length > 10 && d.startsWith('57')) d = d.slice(2); return d.slice(0, 10); };
const esCelularCO = (t) => /^3\d{9}$/.test(soloDigitos(t));
const formatoCel = (t) => { const d = soloDigitos(t); return [d.slice(0, 3), d.slice(3, 6), d.slice(6)].filter(Boolean).join(' '); };
const telParaApi = (t) => { const d = soloDigitos(t); return d ? '+57' + d : null; };
function campoCelular(id, inicial) {
  const input = h('input', { id, type: 'tel', inputmode: 'numeric', autocomplete: 'tel-national', placeholder: '300 123 4567' });
  input.value = formatoCel(inicial);
  // Formatear al salir del campo evita que el cursor salte mientras se edita en el medio.
  input.addEventListener('blur', () => { input.value = formatoCel(input.value); });
  return { input, caja: h('div', { class: 'tel' }, h('span', { class: 'prefijo', 'aria-hidden': 'true' }, '+57'), input) };
}
const esCorreo = (c) => c.length <= 120 && EMAIL_RE.test(c);
const nombreCorto = (usuario) => String(usuario || '').split('@')[0];
function enlaceWhatsApp(t) { return 'https://wa.me/57' + soloDigitos(t); }
/* Las fotos nuevas pueden venir como URL absoluta (Cloudinary) o como nombre local (/api/fotos/...). */
const urlFoto = (url) => (/^https?:\/\//.test(String(url || '')) ? url : API + url);
function avatar(url, nombre, clase, alt) {
  const inicial = (String(nombre || '?').trim()[0] || '?').toUpperCase();
  const etiqueta = alt || '';
  return url ? h('img', { class: 'avatar ' + (clase || ''), src: urlFoto(url), alt: etiqueta })
             : h('span', etiqueta
                 ? { class: 'avatar ' + (clase || ''), role: 'img', 'aria-label': etiqueta }
                 : { class: 'avatar ' + (clase || ''), 'aria-hidden': 'true' }, inicial);
}

/* ---------- sesión ---------- */
function leerSesion() {
  const token = localStorage.getItem(CLAVE_TOKEN);
  if (!token) return null;
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const p = JSON.parse(decodeURIComponent(escape(atob(b64))));
    if (p.exp && p.exp * 1000 < Date.now()) { localStorage.removeItem(CLAVE_TOKEN); return null; }
    const roles = (p.roles || []).map((r) => String(r).replace(/^ROLE_/, ''));
    return { token, usuario: p.sub, roles, esAdmin: roles.includes('ADMIN') };
  } catch { localStorage.removeItem(CLAVE_TOKEN); return null; }
}
function cerrarSesion() { localStorage.removeItem(CLAVE_TOKEN); perfilActual = null; location.hash = '#/login'; }
async function cargarPerfil() {
  try { perfilActual = await api('GET', '/api/perfil'); } catch { /* se reintenta en la siguiente vista */ }
  pintarNav();
}

/* ---------- cliente HTTP ---------- */
async function api(metodo, ruta, cuerpo, esFormulario) {
  const s = leerSesion();
  const cabeceras = {};
  if (s) cabeceras.Authorization = 'Bearer ' + s.token;
  let body;
  if (cuerpo !== undefined) {
    if (esFormulario) body = cuerpo;
    else { cabeceras['Content-Type'] = 'application/json'; body = JSON.stringify(cuerpo); }
  }
  let r;
  try { r = await fetch(API + ruta, { method: metodo, headers: cabeceras, body }); }
  catch { throw new Error('No hay conexión con el servidor'); }
  let datos = null;
  if (r.status !== 204) { try { datos = await r.json(); } catch { /* sin cuerpo */ } }
  if (r.status === 401 && !ruta.startsWith('/api/auth/')) {
    localStorage.removeItem(CLAVE_TOKEN); perfilActual = null; location.hash = '#/login';
    throw new Error(datos && datos.mensaje && /eliminada/.test(datos.mensaje) ? datos.mensaje : 'Tu sesión venció, ingresa de nuevo');
  }
  if (!r.ok) {
    const err = new Error(mensajeDeError(r.status, datos, ruta));
    err.estado = r.status;
    err.codigo = datos && datos.code;
    throw err;
  }
  return datos;
}
function mensajeDeError(estado, d, ruta) {
  // El registro duplicado es el único caso que el servidor explica con jerga ("username"); se traduce.
  if (ruta === '/api/auth/register' && estado === 409) return 'Ese correo ya está registrado.';
  if (estado === 401) return 'Correo o contraseña incorrectos.';
  if (estado === 403) return 'No tienes permiso para esto.';
  if (estado === 404) return 'No se encontró.';
  if (d && Array.isArray(d.detalles) && d.detalles.length) return d.detalles.join('. ');
  if (d && d.mensaje) return d.mensaje;
  if (d && d.error) return d.error;
  if (d && d.message) return d.message;
  if (d && d.errors) return Object.values(d.errors).join('. ');
  return estado === 409 ? 'Ese dato ya existe.' : 'No pudimos completar la acción. Intenta de nuevo.';
}

/* ---------- tema claro / oscuro ----------
   Se guarda 'claro' u 'oscuro' en localStorage; sin valor se sigue la preferencia del sistema.
   tema.js aplica la elección antes del primer pintado; aquí solo se alterna y se anuncian los botones. */
const CLAVE_TEMA = 'upmarket.tema';
function temaGuardado() {
  try { const t = localStorage.getItem(CLAVE_TEMA); return t === 'claro' || t === 'oscuro' ? t : null; }
  catch { return null; }
}
function prefiereOscuro() {
  return Boolean(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
}
function temaEfectivo() {
  const t = temaGuardado();
  if (t) return t;
  return prefiereOscuro() ? 'oscuro' : 'claro';
}
function iconoTema(nombre) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  for (const [k, v] of Object.entries({ viewBox: '0 0 24 24', width: '20', height: '20', fill: 'none',
    stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
    class: 'icono-' + nombre, 'aria-hidden': 'true', focusable: 'false' })) svg.setAttribute(k, v);
  if (nombre === 'luna') {
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z');
    svg.append(path);
  } else {
    const circulo = document.createElementNS(ns, 'circle');
    circulo.setAttribute('cx', '12'); circulo.setAttribute('cy', '12'); circulo.setAttribute('r', '3.5');
    const rayos = document.createElementNS(ns, 'path');
    rayos.setAttribute('d', 'M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4');
    svg.append(circulo, rayos);
  }
  return svg;
}
function botonTema() {
  return h('button', { class: 'boton-tema', type: 'button', 'aria-label': 'Cambiar a modo oscuro',
    'aria-pressed': 'false', onclick: alternarTema }, iconoTema('luna'), iconoTema('sol'));
}
function actualizarBotonesTema() {
  const raiz = document.documentElement;
  const oscuro = raiz.getAttribute('data-tema') === 'oscuro'
    || (!raiz.hasAttribute('data-tema') && prefiereOscuro());
  const etiqueta = oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro';
  document.querySelectorAll('.boton-tema').forEach((boton) => {
    boton.setAttribute('aria-pressed', String(oscuro));
    boton.setAttribute('aria-label', etiqueta);
    boton.setAttribute('title', etiqueta);
  });
}
function alternarTema() {
  const destino = temaEfectivo() === 'oscuro' ? 'claro' : 'oscuro';
  try { localStorage.setItem(CLAVE_TEMA, destino); } catch { /* sin almacenamiento: solo esta sesión */ }
  document.documentElement.setAttribute('data-tema', destino);
  actualizarBotonesTema();
}

/* ---------- navegación ---------- */
function pintarNav() {
  const s = leerSesion(); const nav = document.getElementById('nav'); const menu = document.getElementById('menu'); const ruta = location.hash || '#/';
  nav.classList.remove('abierto');
  menu.setAttribute('aria-expanded', 'false');
  // Sin sesión solo hay dos salidas: ingresar o crear cuenta (la landing las muestra en la barra).
  menu.hidden = false;
  if (!s) {
    nav.hidden = false;
    nav.replaceChildren(
      h('a', { href: '#/login' }, 'Ingresar'),
      h('a', { class: 'btn', href: '#/registro' }, 'Crear cuenta'));
    return;
  }
  nav.hidden = false;
  const enlace = (href, texto, clase) => h('a', { href, class: (ruta === href ? 'activo ' : '') + (clase || '') }, texto);
  const nombre = perfilActual ? perfilActual.nombre : nombreCorto(s.usuario);
  nav.replaceChildren(...[
    enlace('#/', 'Catálogo'),
    s.esAdmin ? null : enlace('#/publicar', 'Publicar', 'cta'),
    s.esAdmin ? null : enlace('#/mias', 'Mis publicaciones'),
    s.esAdmin ? h('a', { href: '/admin/' }, 'Admin') : null,
    h('a', { href: '#/perfil', class: ruta === '#/perfil' ? 'activo' : '' },
      avatar(perfilActual && perfilActual.fotoUrl, nombre, ''), 'Mi perfil'),
    h('button', { onclick: cerrarSesion }, 'Salir')].filter(Boolean));
}

/* ---------- pie de página ----------
   Se repinta junto con la nav en cada cambio de ruta o de sesión (ver enrutar). */
function pintarPie() {
  const pie = document.getElementById('pie');
  if (!pie) return;
  const s = leerSesion();
  const anio = new Date().getFullYear();
  const enlace = (href, texto) => h('li', {}, h('a', { href }, texto));
  const esAdmin = Boolean(s && s.esAdmin);
  const mercado = [
    enlace('#/', 'Buscar'),
    s && !esAdmin ? enlace('#/publicar', 'Publicar') : null,
    s && !esAdmin ? enlace('#/mias', 'Mis publicaciones') : null,
    esAdmin ? enlace('/admin/', 'Panel de administración') : null,
  ].filter(Boolean);
  const ayuda = [
    esAdmin ? null : h('li', {}, h('span', { class: 'pie__dato' }, 'Publicar cuesta $3.000 (pago simulado)')),
    SOPORTE_CORREO ? h('li', {}, h('a', { href: 'mailto:' + SOPORTE_CORREO }, 'Contactar soporte')) : null,
  ].filter(Boolean);
  pie.replaceChildren(
    h('div', { class: 'pie__interior' },
      h('div', { class: 'pie__destacado' },
        h('a', { class: 'marca pie__marca', href: '#/' }, 'UP', h('span', {}, 'Market')),
        h('p', { class: 'pie__lema' }, 'El mercado de los estudiantes de la Universidad de Pamplona, sede Villa del Rosario.'),
        h('a', { class: 'btn pie__accion', href: esAdmin ? '/admin/' : (s ? '#/publicar' : '#/registro') },
          esAdmin ? 'Ir al panel de administración' : (s ? 'Publicar un artículo' : 'Crear cuenta')),
        botonTema()),
      h('nav', { class: 'pie__nav', 'aria-label': 'Pie de página' },
        h('div', { class: 'pie__grupo' },
          h('h2', { class: 'pie__grupo-titulo' }, 'Mercado'),
          h('ul', { class: 'pie__lista' }, mercado)),
        h('div', { class: 'pie__grupo' },
          h('h2', { class: 'pie__grupo-titulo' }, 'Ayuda'),
          h('ul', { class: 'pie__lista' }, ayuda))),
      h('div', { class: 'pie__credito' },
        h('img', { src: 'public/escudo-128.png', alt: 'Escudo de la Universidad de Pamplona', width: 128, height: 167, loading: 'lazy', decoding: 'async' }),
        h('span', {}, 'Proyecto Integrador · Universidad de Pamplona · ' + anio)),
      h('img', { class: 'pie__mascota', src: 'public/mascota-upmarket.png', alt: '', width: 241, height: 360, 'aria-hidden': 'true' })));
  actualizarBotonesTema();
}
document.getElementById('tema').addEventListener('click', alternarTema);
actualizarBotonesTema();
if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').addEventListener) {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', actualizarBotonesTema);
}
document.getElementById('menu').addEventListener('click', () => {
  const nav = document.getElementById('nav'); const abierto = nav.classList.toggle('abierto');
  document.getElementById('menu').setAttribute('aria-expanded', String(abierto));
});
document.querySelector('.salto')?.addEventListener('click', (ev) => {
  ev.preventDefault();
  document.getElementById('app').focus();
});
const rutas = [
  [/^#\/login$/, vistaLogin], [/^#\/registro$/, vistaRegistro],
  [/^#\/verificar(?:\?.*)?$/, vistaVerificar],
  [/^#\/?$/, () => (leerSesion() ? vistaCatalogo() : vistaLanding())],
  [/^#\/publicar$/, () => (leerSesion() && leerSesion().esAdmin ? vistaSoloEstudiantes() : vistaFormulario(null))],
  [/^#\/mias$/, () => (leerSesion() && leerSesion().esAdmin ? vistaSoloEstudiantes() : vistaMias())],
  [/^#\/perfil$/, vistaPerfil],
  [/^#\/vendedor\/(.+)$/, (m) => vistaVendedor(decodeURIComponent(m[1]))],
  [/^#\/pagar\/(\d+)$/, (m) => vistaPago(m[1])],
  [/^#\/p\/(\d+)$/, (m) => vistaDetalle(m[1])], [/^#\/editar\/(\d+)$/, (m) => vistaFormulario(m[1])],
];
const TITULOS = {
  '#/': 'Catálogo', '#/login': 'Ingresar', '#/registro': 'Crear cuenta', '#/publicar': 'Publicar',
  '#/mias': 'Mis publicaciones', '#/perfil': 'Mi perfil',
  '#/verificar': 'Confirmar correo',
};
function tituloDe(hash) {
  if (/^#\/verificar/.test(hash)) return 'Confirmar correo';
  if (/^#\/pagar\//.test(hash)) return 'Pagar publicación';
  if (/^#\/p\//.test(hash)) return 'Detalle de publicación';
  if (/^#\/editar\//.test(hash)) return 'Editar publicación';
  if (/^#\/vendedor\//.test(hash)) return 'Vendedor';
  return TITULOS[hash] || '';
}
async function enrutar() {
  const hash = location.hash || '#/';
  const raiz = hash === '#/' || hash === '#/?';
  const sesion = leerSesion();
  const acceso = hash === '#/login' || hash === '#/registro' || /^#\/verificar(?:\?|$)/.test(hash);
  const publica = acceso || (raiz && !sesion);
  limpiarTemporizadores();
  document.body.classList.toggle('pantalla-acceso', acceso);
  document.body.classList.toggle('pantalla-landing', raiz && !sesion);
  pintarNav();
  pintarPie();
  const titulo = tituloDe(hash);
  document.title = raiz && !sesion ? 'UPMarket · La cartelera de la sede'
    : titulo ? titulo + ' · UPMarket' : 'UPMarket · Mercado de estudiantes';
  if (!publica && !sesion) { location.hash = '#/login'; return; }
  if (!publica && !perfilActual) await cargarPerfil();
  for (const [patron, vista] of rutas) {
    const m = hash.match(patron);
    if (m) {
      try { await vista(m); }
      catch (e) { montar(h('div', { class: 'panel vacio' }, h('p', {}, e.message), h('a', { class: 'btn sec', href: '#/' }, 'Volver al catálogo'))); }
      // Al cambiar de vista el foco se pierde: devolverlo al título, salvo que la vista ya lo haya movido.
      if (document.activeElement === document.body || document.activeElement === null) {
        const h1 = $app().querySelector('h1');
        if (h1) { h1.setAttribute('tabindex', '-1'); h1.focus({ preventScroll: true }); }
      }
      return;
    }
  }
  location.hash = '#/';
}
window.addEventListener('hashchange', enrutar);
window.addEventListener('load', enrutar);

/* ---------- pantallas de acceso compartidas (ingreso, registro, verificación) ---------- */
let temporizadoresVista = [];
function registrarTemporizador(id) { temporizadoresVista.push(id); return id; }
function limpiarTemporizadores() {
  for (const id of temporizadoresVista) clearInterval(id);
  temporizadoresVista = [];
}
function panelMarca() {
  return h('aside', { class: 'acceso__marca', 'aria-label': 'UPMarket' },
    h('p', { class: 'acceso__logo' }, 'UP', h('span', {}, 'Market')),
    h('ul', { class: 'acceso__lista' },
      h('li', {}, 'Publica lo que quieres vender'),
      h('li', {}, 'Encuentra artículos de otros estudiantes'),
      h('li', {}, 'Contacta al vendedor directamente')),
    h('img', { class: 'acceso__mascota', src: 'public/mascota-upmarket.png', alt: '', width: 241, height: 360, 'aria-hidden': 'true' }),
    h('div', {},
      h('p', { class: 'acceso__lema' }, 'Compra, vende e intercambia entre estudiantes de la Universidad de Pamplona.'),
      h('p', { class: 'acceso__lugar' }, 'Villa del Rosario · Norte de Santander')));
}
function volverInicio() {
  return h('a', { class: 'marca acceso__volver', href: '#/', 'aria-label': 'UPMarket, volver al inicio' }, 'UP', h('span', {}, 'Market'));
}
function panelAcceso(...hijos) {
  return h('div', { class: 'acceso' }, panelMarca(), h('div', { class: 'acceso__form' }, volverInicio(), ...hijos));
}

/* Botón «Reenviar correo» con cuenta atrás de 60 s para no saturar el envío. */
function crearReenviar(correo) {
  const boton = h('button', { class: 'btn sec', type: 'button' }, 'Reenviar correo');
  const estado = h('p', { class: 'acceso__pie', role: 'status', 'aria-live': 'polite', hidden: true });
  let restante = 0;
  let temporizador = null;
  const pintar = () => {
    if (restante > 0) { boton.disabled = true; boton.textContent = 'Reenviar en ' + restante + ' s'; }
    else { boton.disabled = false; boton.textContent = 'Reenviar correo'; }
  };
  const esperar = () => {
    clearInterval(temporizador);
    restante = 60;
    pintar();
    temporizador = registrarTemporizador(setInterval(() => {
      restante -= 1;
      if (restante <= 0) { clearInterval(temporizador); temporizador = null; restante = 0; }
      pintar();
    }, 1000));
  };
  boton.addEventListener('click', async () => {
    if (boton.disabled) return;
    const restaurar = cargandoEn(boton, 'Enviando…');
    try {
      await api('POST', '/api/auth/resend-verification', { username: correo });
      estado.textContent = 'Te enviamos otro enlace a ' + correo + '.';
      estado.hidden = false;
      restaurar();
      esperar();
    } catch {
      estado.textContent = 'No pudimos reenviar el correo. Intenta de nuevo.';
      estado.hidden = false;
      restaurar();
      pintar();
    }
  });
  return { boton, estado, esperar };
}

/* Tras registrarse no se inicia sesión: primero hay que confirmar el correo. */
function vistaCorreoEnviado(correo) {
  const reenviar = crearReenviar(correo);
  montar(panelAcceso(
    h('h1', {}, 'Revisa tu correo'),
    h('p', { class: 'acceso__sub' }, 'Te enviamos un enlace a ' + correo + ' para confirmar tu cuenta. El enlace vence en 24 horas.'),
    h('div', { class: 'acciones' }, reenviar.boton),
    reenviar.estado,
    h('p', { class: 'acceso__pie' }, h('a', { href: '#/login' }, 'Ir a ingresar'))));
  reenviar.esperar();
}

/* #/verificar?token=…: confirma el correo y limpia el token de la dirección. */
function vistaVerificar() {
  const hash = location.hash || '';
  const indice = hash.indexOf('?');
  const parametros = new URLSearchParams(indice >= 0 ? hash.slice(indice + 1) : '');
  const token = parametros.get('token') || '';
  // Nunca se registra el token y se quita de la barra de direcciones antes de usarlo.
  try { history.replaceState(null, '', location.pathname + location.search + '#/verificar'); } catch { /* sin history */ }

  const mostrar = (...hijos) => montar(panelAcceso(...hijos));
  const irAIngresar = () => h('p', { class: 'acceso__pie' }, h('a', { href: '#/login' }, 'Ir a ingresar'));

  if (!token) {
    mostrar(h('h1', {}, 'Este enlace no es válido o ya se usó.'), irAIngresar());
    return;
  }

  mostrar(h('h1', {}, 'Confirmando tu correo…'), h('p', { class: 'acceso__sub' }, 'Espera un momento.'));

  const enlaceNuevo = () => {
    const correo = h('input', { id: 'correo-verificar', type: 'email', inputmode: 'email', autocapitalize: 'none', spellcheck: 'false', required: true, autocomplete: 'username', maxlength: 120, placeholder: 'tucorreo@ejemplo.com' });
    const error = h('p', { class: 'error', id: 'error-verificar', role: 'alert', hidden: true });
    correo.setAttribute('aria-describedby', 'error-verificar');
    const estado = h('p', { class: 'acceso__pie', role: 'status', 'aria-live': 'polite', hidden: true });
    const boton = h('button', { class: 'btn', type: 'submit' }, 'Enviar un enlace nuevo');
    return h('form', { novalidate: true, onsubmit: async (ev) => {
      ev.preventDefault();
      error.hidden = true;
      const c = correo.value.trim().toLowerCase();
      if (!esCorreo(c)) {
        error.textContent = 'Escribe un correo válido.';
        error.hidden = false; correo.focus();
        return;
      }
      const restaurarBoton = cargandoEn(boton, 'Enviando…');
      try {
        await api('POST', '/api/auth/resend-verification', { username: c });
        estado.textContent = 'Te enviamos un enlace nuevo a ' + c + '.';
        estado.hidden = false;
        correo.value = '';
      } catch {
        error.textContent = 'No pudimos enviar el enlace. Intenta de nuevo.';
        error.hidden = false;
      } finally { restaurarBoton(); }
    } },
      h('label', { for: 'correo-verificar' }, 'Correo'),
      correo,
      error,
      h('div', { class: 'acciones' }, boton),
      estado);
  };

  api('POST', '/api/auth/verify-email', { token })
    .then(() => {
      mostrar(
        h('h1', {}, '¡Correo confirmado!'),
        h('div', { class: 'acciones' }, h('button', { class: 'btn', type: 'button', onclick: () => { location.hash = '#/login'; } }, 'Ingresar')));
    })
    .catch((e) => {
      if (e && e.codigo === 'TOKEN_EXPIRADO') {
        mostrar(h('h1', {}, 'El enlace venció.'), enlaceNuevo(), irAIngresar());
      } else if (e && e.codigo === 'TOKEN_INVALIDO') {
        mostrar(h('h1', {}, 'Este enlace no es válido o ya se usó.'), irAIngresar());
      } else {
        mostrar(h('h1', {}, 'No pudimos confirmar tu correo.'), h('p', { class: 'acceso__sub' }, 'Revisa tu conexión e intenta de nuevo.'), irAIngresar());
      }
    });
}

/* ---------- ingreso y registro (AuthCore: el nombre de usuario ES el correo) ----------
   Excepción: el administrador inicial se crea con el usuario `admin`, sin correo. */
const esUsuarioLogin = (valor) => !/\s/.test(valor) && (!valor.includes('@') || esCorreo(valor));
function campoClave(id, etiqueta, ayuda, autocompletar) {
  return [h('label', { for: id }, etiqueta, ayuda ? h('small', {}, ' ' + ayuda) : null),
          h('input', { id, type: 'password', required: true, minlength: 6, maxlength: 72, autocomplete: autocompletar || 'new-password' })];
}
async function iniciarSesion(correo, clave) {
  const r = await api('POST', '/api/auth/login', { username: correo, password: clave });
  localStorage.setItem(CLAVE_TOKEN, r.token);
  try { perfilActual = await api('GET', '/api/perfil'); }
  catch (e) { localStorage.removeItem(CLAVE_TOKEN); perfilActual = null; throw e; }
  await aplicarPerfilPendiente(correo);
  location.hash = destinoPendiente();
}

/* El registro ya no crea sesión: el perfil que se escribió allí se aplica en el primer ingreso. */
async function aplicarPerfilPendiente(usuario) {
  let pendiente = null;
  try { pendiente = JSON.parse(localStorage.getItem(CLAVE_PERFIL_PENDIENTE) || 'null'); } catch { pendiente = null; }
  if (!pendiente || pendiente.usuario !== usuario) return;
  try {
    perfilActual = await api('PUT', '/api/perfil', { nombre: pendiente.nombre, telefono: pendiente.telefono, fotoUrl: null });
  } catch { /* si falla, se completa desde Mi perfil */ }
  try { localStorage.removeItem(CLAVE_PERFIL_PENDIENTE); } catch { /* sin localStorage */ }
}

/* S1: desde la landing se recuerda a dónde volver después de ingresar. */
const CLAVE_VOLVER = 'upmarket.volverA';
function irAIngreso(destino) {
  try { sessionStorage.setItem(CLAVE_VOLVER, destino); } catch { /* sin sessionStorage */ }
  location.hash = '#/login';
}
function destinoPendiente() {
  let destino = null;
  try { destino = sessionStorage.getItem(CLAVE_VOLVER); sessionStorage.removeItem(CLAVE_VOLVER); } catch { /* sin sessionStorage */ }
  return destino && destino.startsWith('#/') ? destino : '#/';
}
function vistaLogin() {
  const error = h('p', { class: 'error', id: 'error-login', role: 'alert', hidden: true });
  const ayuda = h('div', { class: 'acciones', hidden: true });
  const correo = h('input', { id: 'correo', type: 'text', inputmode: 'email', autocapitalize: 'none', spellcheck: 'false', required: true, autocomplete: 'username', maxlength: 120, placeholder: 'tucorreo@ejemplo.com o admin', 'aria-describedby': 'error-login' });
  const [lblClave, clave] = campoClave('clave', 'Contraseña', null, 'current-password');
  clave.setAttribute('aria-describedby', 'error-login');
  const ver = h('button', { type: 'button', 'aria-pressed': 'false', 'aria-controls': 'clave' }, 'Mostrar contraseña');
  ver.addEventListener('click', () => {
    const oculta = clave.type === 'password';
    clave.type = oculta ? 'text' : 'password';
    ver.setAttribute('aria-pressed', String(oculta));
    ver.textContent = oculta ? 'Ocultar contraseña' : 'Mostrar contraseña';
    clave.focus();
  });
  const boton = h('button', { class: 'btn', type: 'submit' }, 'Ingresar');
  const fallar = (mensaje, campo) => {
    error.textContent = mensaje; error.hidden = false;
    if (campo) campo.focus();
  };
  montar(h('div', { class: 'acceso' },
    panelMarca(),
    h('form', { class: 'acceso__form', novalidate: true, onsubmit: async (ev) => {
      ev.preventDefault();
      const c = correo.value.trim().toLowerCase();
      const problema = !c ? 'Escribe tu correo o usuario'
        : !esUsuarioLogin(c) ? 'Escribe un correo válido o el usuario admin'
        : !clave.value ? 'Escribe tu contraseña'
        : clave.value.length < 6 ? 'La contraseña debe tener al menos 6 caracteres' : null;
      if (problema) { fallar(problema, !c || !esUsuarioLogin(c) ? correo : clave); return; }
      error.hidden = true;
      ayuda.hidden = true;
      ayuda.replaceChildren();
      limpiarTemporizadores();
      const restaurar = cargandoEn(boton, 'Ingresando…');
      try { await iniciarSesion(c, clave.value); }
      catch (e) {
        if (e.estado === 403 && e.codigo === 'EMAIL_NO_VERIFICADO') {
          fallar('Tu correo aún no está confirmado.', clave);
          const reenviar = crearReenviar(c);
          ayuda.replaceChildren(reenviar.boton, reenviar.estado);
          ayuda.hidden = false;
        } else if (e.estado === 403 && e.codigo === 'CUENTA_BLOQUEADA') {
          fallar('Tu cuenta está bloqueada. Escríbenos si crees que es un error.', clave);
        } else {
          fallar(e.message, clave);
        }
      }
      finally { restaurar(); }
    } },
      h('a', { class: 'marca acceso__volver', href: '#/', 'aria-label': 'UPMarket, volver al inicio' }, 'UP', h('span', {}, 'Market')),
      h('h1', {}, 'Ingresar a UPMarket'),
      h('p', { class: 'acceso__sub' }, 'Tus publicaciones, tus ventas y tus mensajes.'),
      h('label', { for: 'correo' }, 'Correo o usuario'), correo,
      lblClave, clave,
      h('div', { class: 'ver-clave' }, ver),
      error,
      ayuda,
      h('div', { class: 'acciones' }, boton),
      h('p', { class: 'acceso__pie' }, '¿No tienes cuenta? ', h('a', { href: '#/registro' }, 'Crea tu cuenta')))));
  if (window.matchMedia('(pointer:fine)').matches) correo.focus();
}
function vistaRegistro() {
  const nombre = h('input', { id: 'nombre', required: true, minlength: 2, maxlength: 60, autocomplete: 'name' });
  const correo = h('input', { id: 'correo', type: 'email', inputmode: 'email', autocapitalize: 'none', spellcheck: 'false', required: true, autocomplete: 'username', maxlength: 120, placeholder: 'tucorreo@ejemplo.com' });
  const telC = campoCelular('tel', ''); const tel = telC.input;
  const [lblClave, clave] = campoClave('clave', 'Contraseña', '(mínimo 6 caracteres)');
  const [lblClave2, clave2] = campoClave('clave2', 'Repite la contraseña');

  const errNombre = h('p', { class: 'campo-error', id: 'error-nombre', hidden: true });
  const errCorreo = h('p', { class: 'campo-error', id: 'error-correo', hidden: true });
  const errClave = h('p', { class: 'campo-error', id: 'error-clave', hidden: true });
  const errClave2 = h('p', { class: 'campo-error', id: 'error-clave2', hidden: true });
  const errTel = h('p', { class: 'campo-error', id: 'error-tel', hidden: true });
  const error = h('div', { class: 'error', role: 'alert', tabindex: '-1', hidden: true });

  const pares = [[nombre, errNombre], [correo, errCorreo], [clave, errClave], [clave2, errClave2], [tel, errTel]];
  for (const [campo, cajaError] of pares) {
    campo.setAttribute('aria-describedby', cajaError.id);
    campo.addEventListener('input', () => {
      if (cajaError.hidden) return;
      cajaError.hidden = true;
      campo.removeAttribute('aria-invalid');
    });
  }

  const ver = h('button', { type: 'button', 'aria-pressed': 'false', 'aria-controls': 'clave clave2' }, 'Mostrar contraseñas');
  ver.addEventListener('click', () => {
    const ocultas = clave.type === 'password';
    clave.type = ocultas ? 'text' : 'password';
    clave2.type = clave.type;
    ver.setAttribute('aria-pressed', String(ocultas));
    ver.textContent = ocultas ? 'Ocultar contraseñas' : 'Mostrar contraseñas';
    clave.focus();
  });

  const boton = h('button', { class: 'btn', type: 'submit' }, 'Crear cuenta');
  const marcar = (campo, cajaError, mensaje) => {
    cajaError.textContent = mensaje; cajaError.hidden = false;
    campo.setAttribute('aria-invalid', 'true');
  };
  const fallar = (mensaje) => {
    error.textContent = mensaje; error.hidden = false; error.focus();
  };

  montar(h('div', { class: 'acceso' },
    panelMarca(),
    h('form', { class: 'acceso__form', novalidate: true, onsubmit: async (ev) => {
      ev.preventDefault();
      error.hidden = true;
      for (const [campo, cajaError] of pares) { cajaError.hidden = true; campo.removeAttribute('aria-invalid'); }
      const c = correo.value.trim().toLowerCase();
      const fallos = [];
      if (nombre.value.trim().length < 2) fallos.push([nombre, errNombre, 'Escribe tu nombre.']);
      if (!c) fallos.push([correo, errCorreo, 'Escribe tu correo.']);
      else if (!esCorreo(c)) fallos.push([correo, errCorreo, 'Escribe un correo válido, por ejemplo nombre@unipamplona.edu.co.']);
      if (!clave.value) fallos.push([clave, errClave, 'Escribe una contraseña.']);
      else if (clave.value.length < 6) fallos.push([clave, errClave, 'Usa al menos 6 caracteres.']);
      if (!clave2.value) fallos.push([clave2, errClave2, 'Repite la contraseña.']);
      else if (clave.value !== clave2.value) fallos.push([clave2, errClave2, 'Las contraseñas no coinciden.']);
      if (soloDigitos(tel.value) && !esCelularCO(tel.value)) fallos.push([tel, errTel, MSG_CEL]);
      if (fallos.length) {
        for (const [campo, cajaError, mensaje] of fallos) marcar(campo, cajaError, mensaje);
        fallos[0][0].focus();
        return;
      }
      const restaurar = cargandoEn(boton, 'Creando cuenta…');
      try {
        await api('POST', '/api/auth/register', { username: c, password: clave.value });
      } catch (e) {
        if (e.estado === 409) { marcar(correo, errCorreo, 'Ese correo ya está registrado. Ingresa o usa otro.'); correo.focus(); }
        else if (e.estado === 400) fallar('Hay un dato que no cumple el formato. Revísalo e intenta de nuevo.');
        else fallar(e.message);
        restaurar();
        return;
      }
      // Sin sesión todavía no se puede crear el perfil: se guarda hasta el primer ingreso.
      try {
        localStorage.setItem(CLAVE_PERFIL_PENDIENTE, JSON.stringify({
          usuario: c, nombre: nombre.value.trim(), telefono: telParaApi(tel.value),
        }));
      } catch { /* sin localStorage: el perfil se completa después */ }
      vistaCorreoEnviado(c);
    } },
      h('a', { class: 'marca acceso__volver', href: '#/', 'aria-label': 'UPMarket, volver al inicio' }, 'UP', h('span', {}, 'Market')),
      h('h1', {}, 'Crea tu cuenta'),
      h('p', { class: 'acceso__sub' }, 'Compra y vende artículos con estudiantes de la Universidad de Pamplona.'),
      h('label', { for: 'nombre' }, 'Nombre'), nombre, errNombre,
      h('label', { for: 'correo' }, 'Correo ', h('small', {}, 'Con este correo ingresarás')), correo, errCorreo,
      lblClave, clave, errClave,
      h('div', { class: 'ver-clave' }, ver),
      lblClave2, clave2, errClave2,
      h('label', { for: 'tel' }, 'Celular ', h('small', {}, '(opcional, Colombia +57)')), telC.caja, errTel,
      error,
      h('div', { class: 'acciones' }, boton),
      h('p', { class: 'acceso__pie' }, '¿Ya tienes cuenta? ', h('a', { href: '#/login' }, 'Ingresa')))));
}

/* ---------- landing pública (#/ sin sesión): la cartelera de la sede ---------- */
function vistaLanding() {
  const sinMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const estado = { texto: '', categoria: '' };
  let categorias = [];
  let turno = 0;

  const entrada = h('input', { id: 'cartelera-texto', type: 'search', maxlength: 80, placeholder: 'Busca un libro, un portátil, unas arepas…', autocomplete: 'off', 'aria-describedby': 'cartelera-ayuda' });
  const cuenta = h('p', { class: 'cartelera__cuenta', 'aria-live': 'polite' });
  const tablero = h('ul', { class: 'cartelera__avisos' });
  const chips = h('div', { class: 'cartelera__chips', role: 'group', 'aria-label': 'Categorías de la cartelera' });

  const lupa = h('span', { class: 'cartelera-buscar__lupa', 'aria-hidden': 'true' });
  lupa.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" focusable="false"><circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';

  const botonBuscarLanding = h('button', { class: 'btn', type: 'submit' }, 'Buscar');
  const formulario = h('form', { class: 'cartelera-buscar', role: 'search', onsubmit: (ev) => {
    ev.preventDefault();
    estado.texto = entrada.value.trim();
    const restaurar = cargandoEn(botonBuscarLanding, 'Buscando…');
    buscar().finally(restaurar);
  } },
    h('label', { for: 'cartelera-texto' }, 'Buscar en la cartelera'),
    h('div', { class: 'cartelera-buscar__fila' },
      lupa, entrada, botonBuscarLanding),
    h('p', { class: 'cartelera-buscar__ayuda', id: 'cartelera-ayuda' }, 'Puedes buscar por palabra y filtrar por categoría.'));

  function pintarChips() {
    const opciones = ['Todo', ...categorias];
    chips.replaceChildren(...opciones.map((nombre) => {
      const valor = nombre === 'Todo' ? '' : nombre;
      const activa = estado.categoria === valor;
      return h('button', { type: 'button', class: 'chip', 'aria-pressed': String(activa), onclick: () => {
        estado.categoria = valor;
        pintarChips();
        // El repintado reemplaza los botones: devolver el foco al chip elegido.
        const elegido = Array.from(chips.querySelectorAll('.chip')).find((b) => b.textContent === nombre);
        if (elegido) elegido.focus();
        buscar();
      } }, nombre);
    }));
  }

  function avisoItem(a, i) {
    const rotacion = ((((Number(a.id) % 7) + 7) % 7) - 3) * 0.5;
    const estadoTexto = a.estadoArticulo === 'NUEVO' ? 'Nuevo' : 'Usado';
    return h('li', { class: 'cartelera__aviso', style: `--rot:${rotacion}deg;--i:${i}` },
      h('a', { class: 'cartelera__enlace', href: '#/login', onclick: (ev) => {
        ev.preventDefault();
        irAIngreso('#/p/' + a.id);
      } },
        h('span', { class: 'cartelera__chincheta', 'aria-hidden': 'true' }),
        a.fotoUrl
          ? h('img', { class: 'cartelera__foto', src: urlFoto(a.fotoUrl), alt: a.titulo, width: 480, height: 320, loading: 'lazy', decoding: 'async' })
          : h('span', { class: 'cartelera__foto cartelera__foto--vacia' }, h('span', {}, a.categoria)),
        h('span', { class: 'cartelera__papel' },
          h('span', { class: 'cartelera__precio' }, precio(a.precio)),
          h('span', { class: 'cartelera__aviso-titulo' }, a.titulo),
          h('span', { class: 'cartelera__aviso-meta' }, a.categoria + ' · ' + estadoTexto))));
  }

  function esqueleto() {
    return h('li', { class: 'cartelera__aviso cartelera__aviso--hueso', 'aria-hidden': 'true' },
      h('span', { class: 'cartelera__foto cartelera__foto--vacia' }),
      h('span', { class: 'cartelera__papel' },
        h('span', { class: 'cartelera__hueso cartelera__hueso--precio' }),
        h('span', { class: 'cartelera__hueso cartelera__hueso--titulo' }),
        h('span', { class: 'cartelera__hueso cartelera__hueso--meta' })));
  }

  function mensajeAvisos(nodo) {
    tablero.classList.remove('sale');
    tablero.replaceChildren(nodo);
  }

  function mensajeVacio() {
    const filtrando = Boolean(estado.texto || estado.categoria);
    if (!filtrando) {
      mensajeAvisos(h('li', { class: 'cartelera__vacio' },
        h('p', {}, 'La cartelera está vacía por ahora. Sé el primero en publicar.'),
        h('a', { class: 'btn', href: '#/registro' }, 'Crear cuenta')));
      return;
    }
    const texto = estado.texto
      ? 'No hay avisos con «' + estado.texto + '»' + (estado.categoria ? ' en ' + estado.categoria : '') + '. Prueba con otra palabra o categoría.'
      : 'No hay avisos en ' + estado.categoria + '. Prueba con otra palabra o categoría.';
    mensajeAvisos(h('li', { class: 'cartelera__vacio' }, h('p', {}, texto)));
  }

  function mensajeError() {
    mensajeAvisos(h('li', { class: 'cartelera__vacio cartelera__vacio--error' },
      h('p', {}, 'No pudimos cargar la cartelera.'),
      h('button', { class: 'btn sec', type: 'button', onclick: buscar }, 'Reintentar')));
  }

  async function buscar() {
    const miTurno = ++turno;
    tablero.setAttribute('aria-busy', 'true');
    cuenta.textContent = 'Cargando avisos…';
    if (!tablero.children.length) tablero.replaceChildren(...Array.from({ length: 8 }, esqueleto));
    const params = new URLSearchParams({ limite: '12' });
    if (estado.texto) params.set('texto', estado.texto);
    if (estado.categoria) params.set('categoria', estado.categoria);
    let avisos;
    try {
      avisos = await api('GET', '/api/publico/cartelera?' + params.toString());
    } catch {
      if (miTurno !== turno) return;
      tablero.removeAttribute('aria-busy');
      cuenta.textContent = '0 avisos';
      mensajeError();
      return;
    }
    if (miTurno !== turno) return;
    tablero.removeAttribute('aria-busy');
    cuenta.textContent = avisos.length + (avisos.length === 1 ? ' aviso' : ' avisos');
    if (!avisos.length) { mensajeVacio(); return; }
    const reemplazar = () => {
      if (miTurno !== turno) return;
      tablero.classList.remove('sale');
      const items = avisos.map(avisoItem);
      tablero.replaceChildren(...items);
      items.forEach((li) => li.classList.add('cae'));
    };
    if (tablero.querySelector('.cartelera__aviso:not(.cartelera__aviso--hueso)') && !sinMovimiento) {
      tablero.classList.add('sale');
      setTimeout(reemplazar, 120);
    } else {
      reemplazar();
    }
  }

  const verTodo = h('a', { class: 'cartelera__ver', href: '#/login', onclick: (ev) => {
    ev.preventDefault();
    irAIngreso('#/');
  } }, 'Ver todo el catálogo');

  const secciones = [];
  const seccion = (clase, ...nodos) => {
    const s = h('section', { class: 'cartelera-seccion' + (clase ? ' ' + clase : '') }, h('div', { class: 'cartelera-ancho' }, ...nodos));
    secciones.push(s);
    return s;
  };

  // Cada frase se parte exactamente en "Aquí": el texto no cambia, solo se reparte en dos columnas.
  const filaMotivo = (frase) => {
    const corte = frase.indexOf('Aquí');
    return h('li', { class: 'cartelera-motivos__fila' },
      h('span', { class: 'cartelera-motivos__grupo' }, frase.slice(0, corte)),
      h('span', { class: 'cartelera-motivos__aqui' }, frase.slice(corte)));
  };

  const raizLanding = h('div', { class: 'cartelera' },
    h('section', { class: 'cartelera-hero' },
      h('div', { class: 'cartelera-ancho' },
        h('h1', {}, 'La cartelera de la sede.'),
        h('p', { class: 'cartelera-hero__bajada' }, 'Compra y vende entre estudiantes de la Universidad de Pamplona, sede Villa del Rosario. Cada aviso queda ordenado por categoría y no se pierde en el chat.'),
        formulario,
        chips)),
    h('section', { class: 'cartelera-tablero', 'aria-labelledby': 'cartelera-titulo' },
      h('div', { class: 'cartelera-ancho' },
        h('div', { class: 'cartelera-tablero__cab' },
          h('h2', { id: 'cartelera-titulo' }, 'Lo que hay en la cartelera'),
          cuenta),
        tablero,
        h('p', { class: 'cartelera-tablero__pie' }, verTodo))),
    seccion('cartelera-como',
      h('h2', {}, 'Así funciona'),
      h('div', { class: 'cartelera-rol' },
        h('article', { class: 'cartelera-rol__aviso' },
          h('span', { class: 'cartelera-rol__chincheta', 'aria-hidden': 'true' }),
          h('span', { class: 'cartelera-rol__tab cartelera-rol__tab--gratis' }, 'Gratis'),
          h('h3', {}, 'Si buscas algo'),
          h('p', {}, 'Buscar es gratis. Filtra por categoría, precio o estado, abre el aviso y escríbele al vendedor por WhatsApp o correo. La compra la acuerdan entre ustedes.')),
        h('article', { class: 'cartelera-rol__aviso' },
          h('span', { class: 'cartelera-rol__chincheta', 'aria-hidden': 'true' }),
          h('span', { class: 'cartelera-rol__tab' }, '$3.000'),
          h('h3', {}, 'Si vendes algo'),
          h('p', {}, 'Publicar cuesta $3.000 por aviso (por ahora el pago es simulado). Tu aviso queda visible hasta que lo marques como vendido, y ves cuántas personas lo abrieron.')))),
    seccion('cartelera-porque',
      h('h2', {}, 'Por qué no el grupo de WhatsApp'),
      h('ul', { class: 'cartelera-motivos' },
        filaMotivo('En el grupo, tu oferta se hunde entre saludos y memes en pocas horas. Aquí se queda hasta que la vendas.'),
        filaMotivo('En el grupo no hay categorías. Aquí buscas por categoría, precio o palabra.'),
        filaMotivo('En el grupo tienes que preguntar si alguien vende algo. Aquí lo buscas tú, cuando quieras.'))),
    seccion('cartelera-final',
      h('div', { class: 'cartelera-cierre' },
        h('img', { class: 'cartelera-cierre__mascota', src: 'public/mascota-upmarket.png', alt: '', width: 241, height: 360, loading: 'lazy', 'aria-hidden': 'true' }),
        h('div', { class: 'cartelera-cierre__texto' },
          h('h2', {}, '¿Algo que ya no usas?'),
          h('p', {}, 'Publícalo por $3.000 y deja que lo encuentre quien lo necesita.'),
          h('div', { class: 'cartelera-cierre__acciones' },
            h('a', { class: 'btn', href: '#/registro' }, 'Crear cuenta'),
            h('a', { class: 'cartelera-cierre__enlace', href: '#/login' }, 'Ya tengo cuenta'))))));
  montar(raizLanding);

  // Aparición una sola vez al entrar en pantalla; sin movimiento, todo queda visible.
  if (!sinMovimiento && 'IntersectionObserver' in window) {
    // El opt-in del script activa el estado oculto del CSS; sin él, todo se ve.
    raizLanding.classList.add('animar');
    const observador = new IntersectionObserver((entradas) => {
      for (const e of entradas) {
        if (e.isIntersecting) { e.target.classList.add('entra'); observador.unobserve(e.target); }
      }
    }, { threshold: 0.2 });
    for (const s of secciones) observador.observe(s);
  } else {
    for (const s of secciones) s.classList.add('entra');
  }

  pintarChips();
  buscar();
  api('GET', '/api/publico/categorias')
    .then((lista) => { categorias = Array.isArray(lista) ? lista : []; if (estado.categoria === '') pintarChips(); })
    .catch(() => { /* la cartelera sigue funcionando sin chips de categoría */ });
}

/* ---------- catálogo y búsqueda (HU-10) ---------- */
function tarjeta(p) {
  const foto = p.fotos && p.fotos[0]
    ? h('img', { class: 'foto', src: urlFoto(p.fotos[0].url), alt: '', loading: 'lazy', decoding: 'async' })
    : h('div', { class: 'foto' }, 'Sin foto');
  return h('a', { class: 'tarjeta item', href: '#/p/' + p.id }, foto,
    h('div', { class: 'cuerpo' },
      h('span', { class: 'precio' }, precio(p.precio)),
      h('h3', {}, p.titulo),
      h('span', { class: 'meta' }, p.categoria + ' · ' + nombreCorto(p.vendedor)),
      h('div', { class: 'item__estado' }, indicadoresPublicacion(p, { articulo: true }))));
}
function esqueletoCatalogo() {
  return h('div', { class: 'rejilla', 'aria-hidden': 'true' },
    Array.from({ length: 6 }, () => h('div', { class: 'tarjeta esqueleto' },
      h('div', { class: 'foto-esq' }),
      h('div', { class: 'cuerpo' },
        h('span', { class: 'hueso' }),
        h('span', { class: 'hueso corto' }),
        h('span', { class: 'hueso mini' })))));
}
/* Catálogo (#/): búsqueda grande, chips de categoría y filtros secundarios en una fila.
   Los chips, el estado y el orden aplican al instante; el texto, al enviar o con Enter. */
async function vistaCatalogo() {
  const estado = { texto: '', categoria: '', min: '', max: '', articulo: '', orden: 'recientes' };
  const miles = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
  const soloNumero = (valor) => (valor || '').replace(/\D/g, '');
  const numero = (valor) => { const d = soloNumero(valor); return d ? Number(d) : null; };
  const formatoMiles = (valor) => { const n = soloNumero(valor); return n ? miles.format(Number(n)) : ''; };
  let cats = [];
  let turno = 0;

  const lupa = h('span', { class: 'catalogo-buscar__lupa', 'aria-hidden': 'true' });
  lupa.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" focusable="false"><circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';
  const entrada = h('input', { id: 'q', type: 'search', maxlength: 100, placeholder: 'Calculadora, libro de cálculo…', autocomplete: 'off' });
  const botonBuscar = h('button', { class: 'btn', type: 'submit' }, 'Buscar');
  const formulario = h('form', { class: 'catalogo-buscar', role: 'search', onsubmit: (ev) => {
    ev.preventDefault();
    estado.texto = entrada.value.trim();
    const restaurar = cargandoEn(botonBuscar, 'Buscando…');
    buscar().finally(restaurar);
  } },
    h('label', { class: 'sr-only', for: 'q' }, 'Buscar en el catálogo'),
    h('div', { class: 'catalogo-buscar__fila' }, lupa, entrada, botonBuscar));

  const chips = h('div', { class: 'catalogo-chips', role: 'group', 'aria-label': 'Categorías' });
  function pintarChips() {
    const opciones = ['Todas', ...cats.map((c) => c.nombre)];
    chips.replaceChildren(...opciones.map((nombre) => {
      const valor = nombre === 'Todas' ? '' : nombre;
      return h('button', { type: 'button', class: 'chip', 'aria-pressed': String(estado.categoria === valor),
        onclick: () => { estado.categoria = valor; pintarChips(); buscar(); } }, nombre);
    }));
  }

  const errorFiltros = h('p', { class: 'error', id: 'error-filtros', role: 'alert', hidden: true });
  const min = h('input', { id: 'min', type: 'text', inputmode: 'numeric', autocomplete: 'off', placeholder: '0', 'aria-label': 'Precio mínimo', 'aria-describedby': 'error-filtros' });
  const max = h('input', { id: 'max', type: 'text', inputmode: 'numeric', autocomplete: 'off', placeholder: 'Sin tope', 'aria-label': 'Precio máximo', 'aria-describedby': 'error-filtros' });
  min.addEventListener('input', () => { estado.min = min.value; errorFiltros.hidden = true; });
  max.addEventListener('input', () => { estado.max = max.value; errorFiltros.hidden = true; });
  min.addEventListener('blur', () => { min.value = formatoMiles(min.value); estado.min = min.value; });
  max.addEventListener('blur', () => { max.value = formatoMiles(max.value); estado.max = max.value; });

  const segmentos = [
    { valor: '', texto: 'Cualquiera' }, { valor: 'NUEVO', texto: 'Nuevo' }, { valor: 'USADO', texto: 'Usado' },
  ].map((opcion) => ({ ...opcion,
    boton: h('button', { type: 'button', class: 'segmentado__op', 'aria-pressed': 'false',
      onclick: () => { estado.articulo = opcion.valor; buscar(); } }, opcion.texto) }));
  const selectorEstado = h('div', { class: 'segmentado', role: 'group', 'aria-label': 'Estado del artículo' },
    segmentos.map((s) => s.boton));

  const orden = h('select', { id: 'orden' },
    h('option', { value: 'recientes' }, 'Más recientes'),
    h('option', { value: 'menor' }, 'Menor precio'),
    h('option', { value: 'mayor' }, 'Mayor precio'));
  orden.addEventListener('change', () => { estado.orden = orden.value; buscar(); });

  const limpiar = h('button', { class: 'enlace-filtro', type: 'button', hidden: true, onclick: () => limpiarFiltros() }, 'Limpiar filtros');
  const panel = h('div', { class: 'catalogo-panel', id: 'filtros-catalogo' },
    h('div', { class: 'filtro-grupo' },
      h('span', { class: 'filtro-grupo__titulo' }, 'Precio'),
      h('div', { class: 'filtro-precio' },
        h('span', { class: 'precio-campo' }, h('span', { class: 'prefijo', 'aria-hidden': 'true' }, '$'), min),
        h('span', { class: 'precio-campo' }, h('span', { class: 'prefijo', 'aria-hidden': 'true' }, '$'), max))),
    h('div', { class: 'filtro-grupo' }, h('span', { class: 'filtro-grupo__titulo' }, 'Estado'), selectorEstado),
    h('div', { class: 'filtro-grupo' }, h('label', { for: 'orden' }, 'Ordenar'), orden),
    h('div', { class: 'filtro-grupo filtro-grupo--fin' }, limpiar),
    errorFiltros);

  const botonFiltros = h('button', { class: 'btn sec catalogo-filtros__toggle', type: 'button',
    'aria-expanded': 'false', 'aria-controls': 'filtros-catalogo', onclick: () => {
      const abierto = botonFiltros.getAttribute('aria-expanded') === 'true';
      botonFiltros.setAttribute('aria-expanded', String(!abierto));
      panel.classList.toggle('abierto', !abierto);
    } }, 'Filtros');

  const chipsActivos = h('div', { class: 'chips-activos', 'aria-label': 'Filtros activos' });
  const info = h('p', { class: 'catalogo-cuenta', role: 'status', 'aria-live': 'polite' });
  const resultados = h('div', { 'aria-busy': 'false' });

  function activos() {
    return [estado.texto, estado.categoria, numero(estado.min) !== null, numero(estado.max) !== null, estado.articulo].filter(Boolean).length;
  }
  function pintarActivos() {
    const quitar = (etiqueta, limpiarFiltro) => h('button', { class: 'chip-activo', type: 'button',
      onclick: () => { limpiarFiltro(); buscar(); } }, etiqueta, iconoSvg('M6 6l12 12M18 6L6 18', 12));
    const items = [];
    if (estado.texto) items.push(quitar('«' + estado.texto + '»', () => { estado.texto = ''; entrada.value = ''; }));
    if (estado.categoria) items.push(quitar(estado.categoria, () => { estado.categoria = ''; pintarChips(); }));
    if (estado.min && numero(estado.min) !== null) items.push(quitar('Desde ' + precio(numero(estado.min)), () => { estado.min = ''; min.value = ''; }));
    if (estado.max && numero(estado.max) !== null) items.push(quitar('Hasta ' + precio(numero(estado.max)), () => { estado.max = ''; max.value = ''; }));
    if (estado.articulo) items.push(quitar(estado.articulo === 'NUEVO' ? 'Nuevo' : 'Usado', () => { estado.articulo = ''; }));
    chipsActivos.replaceChildren(...items);
    chipsActivos.hidden = !items.length;
  }
  function actualizarPanel() {
    const total = activos();
    botonFiltros.replaceChildren('Filtros', total ? h('span', { class: 'cuenta' }, String(total)) : '');
    limpiar.hidden = total === 0;
    segmentos.forEach((s) => s.boton.setAttribute('aria-pressed', String(estado.articulo === s.valor)));
    pintarActivos();
  }
  function limpiarFiltros() {
    estado.texto = ''; estado.categoria = ''; estado.min = ''; estado.max = ''; estado.articulo = ''; estado.orden = 'recientes';
    entrada.value = ''; min.value = ''; max.value = ''; orden.value = 'recientes';
    errorFiltros.hidden = true;
    pintarChips();
    buscar();
  }
  function ordenar(lista) {
    const copia = [...lista];
    if (estado.orden === 'menor') copia.sort((a, b) => Number(a.precio) - Number(b.precio));
    else if (estado.orden === 'mayor') copia.sort((a, b) => Number(b.precio) - Number(a.precio));
    else copia.sort((a, b) => new Date(b.publicadaEn) - new Date(a.publicadaEn));
    return copia;
  }
  function panelVacio(hayFiltros) {
    return h('div', { class: 'panel vacio' },
      h('p', {}, hayFiltros ? 'No encontramos publicaciones con esos filtros.' : 'Todavía no hay publicaciones. ¡Sé el primero en publicar!'),
      h('div', { class: 'acciones' },
        hayFiltros ? h('button', { class: 'btn sec', type: 'button', onclick: () => limpiarFiltros() }, 'Limpiar filtros') : null,
        leerSesion() && !leerSesion().esAdmin ? h('a', { class: 'btn', href: '#/publicar' }, 'Publicar un artículo') : null));
  }

  async function buscar() {
    const miTurno = ++turno;
    errorFiltros.hidden = true;
    const minV = numero(estado.min);
    const maxV = numero(estado.max);
    if (minV !== null && maxV !== null && minV > maxV) {
      errorFiltros.textContent = 'El precio mínimo no puede ser mayor que el máximo.';
      errorFiltros.hidden = false;
      min.focus();
      return;
    }
    const ps = new URLSearchParams();
    if (estado.texto) ps.set('texto', estado.texto);
    if (estado.categoria) ps.set('categoria', estado.categoria);
    if (minV !== null) ps.set('precioMin', String(minV));
    if (maxV !== null) ps.set('precioMax', String(maxV));
    if (estado.articulo) ps.set('estado', estado.articulo);
    const hayFiltros = ps.toString() !== '';
    actualizarPanel();
    resultados.setAttribute('aria-busy', 'true');
    resultados.replaceChildren(esqueletoCatalogo());
    info.textContent = 'Cargando publicaciones…';
    try {
      const lista = await api('GET', '/api/publicaciones' + (hayFiltros ? '?' + ps : ''));
      if (miTurno !== turno) return;
      const ordenadas = ordenar(lista);
      info.textContent = ordenadas.length === 1 ? '1 publicación' : ordenadas.length + ' publicaciones';
      resultados.replaceChildren(ordenadas.length
        ? h('div', { class: 'rejilla catalogo-rejilla' }, ordenadas.map(tarjeta))
        : panelVacio(hayFiltros));
    } catch (e) {
      if (miTurno !== turno) return;
      info.textContent = '0 publicaciones';
      resultados.replaceChildren(h('div', { class: 'panel vacio' },
        h('p', {}, 'No se pudieron cargar las publicaciones.'),
        h('p', { class: 'meta' }, e.message),
        h('div', { class: 'acciones' }, h('button', { class: 'btn', type: 'button', onclick: () => buscar() }, 'Reintentar'))));
      aviso(e.message, true);
    } finally {
      if (miTurno === turno) resultados.setAttribute('aria-busy', 'false');
    }
  }

  montar(
    h('h1', {}, 'Catálogo'),
    h('p', { class: 'sub' }, 'Lo que tus compañeros están vendiendo, ordenado y en un solo lugar.'),
    formulario,
    chips,
    botonFiltros,
    panel,
    chipsActivos,
    info,
    resultados);

  pintarChips();
  actualizarPanel();
  try {
    cats = await api('GET', '/api/categorias');
    pintarChips();
  } catch (e) {
    aviso('No se pudieron cargar las categorías: ' + e.message, true);
  }
  await buscar();
}

/* ---------- pago de publicación (modelo de negocio) ---------- */
async function vistaPago(id) {
  const p = await api('GET', '/api/publicaciones/' + id);
  document.title = 'Pagar publicación · UPMarket';
  if (p.estadoPago === 'APROBADO') { location.hash = '#/p/' + id; return; }
  const panel = h('div', { class: 'panel' },
    h('p', {}, 'Publicar cuesta $3.000 (pago simulado).'),
    h('p', { class: 'detalle-precio' }, precio(3000)),
    h('p', { class: 'sub' }, '«' + p.titulo + '» quedará visible en el catálogo apenas se apruebe el pago.'),
    h('button', { class: 'btn', type: 'button', onclick: async (ev) => {
      const boton = ev.currentTarget;
      const restaurar = cargandoEn(boton, 'Pagando…');
      try {
        await api('POST', `/api/publicaciones/${id}/pago`);
        aviso('Pago aprobado');
        document.title = 'Pago aprobado · UPMarket';
        panel.replaceChildren(
          h('h2', { class: 'detalle-sub' }, 'Pago aprobado'),
          h('p', { class: 'sub' }, '«' + p.titulo + '» ya está visible en el catálogo.'),
          accionesRecibo(id),
          h('div', { class: 'acciones' }, h('a', { class: 'btn sec', href: '#/p/' + id }, 'Ver publicación')));
      } catch (e) {
        aviso(e.message, true);
        restaurar();
      }
    } }, 'Pagar'));
  montar(
    h('a', { class: 'volver', href: '#/mias' }, '← Volver a mis publicaciones'),
    h('h1', {}, 'Pagar publicación'),
    panel);
}

/* ---------- detalle y contacto (HU-11) ---------- */
function esqueletoDetalle() {
  return h('div', { class: 'detalle-esqueleto', 'aria-hidden': 'true' },
    h('span', { class: 'hueso detalle-esqueleto__titulo' }),
    h('span', { class: 'hueso detalle-esqueleto__linea' }),
    h('div', { class: 'detalle' },
      h('span', { class: 'hueso detalle-esqueleto__foto' }),
      h('div', { class: 'panel' },
        h('span', { class: 'hueso' }),
        h('span', { class: 'hueso' }),
        h('span', { class: 'hueso corto' }),
        h('span', { class: 'hueso detalle-esqueleto__bloque' }))));
}

async function vistaDetalle(id) {
  montar(esqueletoDetalle());
  const p = await api('GET', '/api/publicaciones/' + id);
  document.title = p.titulo + ' · UPMarket';
  const s = leerSesion();
  const esMia = s && s.usuario === p.vendedor;
  const esAdmin = Boolean(s && s.esAdmin);
  const disponible = p.estadoPublicacion === 'DISPONIBLE';
  const total = p.fotos.length;

  const vend = { nombre: nombreCorto(p.vendedor), fotoUrl: null };
  const avatarVendedor = avatar(null, vend.nombre, 'mediano');
  const nombreVendedor = h('a', { class: 'vendedor__enlace', href: '#/vendedor/' + encodeURIComponent(p.vendedor) }, vend.nombre);
  const resumenVendedor = h('div', { class: 'meta vendedor__resumen' }, '');
  const zonaResena = h('div', { class: 'zona-resena' });
  let datosResenas = null;

  function pintarBotonResena() {
    if (esMia) return;
    if (s && datosResenas && datosResenas.resenas.some((r) => r.autor === s.usuario)) return;
    zonaResena.replaceChildren(h('button', {
      class: 'btn sec', type: 'button',
      onclick: () => {
        if (!leerSesion()) { location.hash = '#/login'; return; }
        zonaResena.replaceChildren(formularioResena(p.vendedor, encodeURIComponent(p.vendedor), () => vistaDetalle(id)));
        const campo = zonaResena.querySelector('#estrellas');
        if (campo) campo.focus();
      },
    }, 'Dejar reseña al vendedor'));
  }

  api('GET', '/api/perfil/publico?usuario=' + encodeURIComponent(p.vendedor))
    .then((perfil) => {
      if (!perfil) return;
      if (perfil.nombre) { vend.nombre = perfil.nombre; nombreVendedor.textContent = perfil.nombre; }
      avatarVendedor.replaceWith(avatar(perfil.fotoUrl, vend.nombre, 'mediano'));
    })
    .catch(() => { /* se mantiene el nombre corto */ });
  api('GET', '/api/perfil/' + encodeURIComponent(p.vendedor) + '/resenas')
    .then((r) => {
      datosResenas = r;
      if (r.total) {
        resumenVendedor.replaceChildren(
          h('span', { class: 'estrellas', 'aria-label': 'Promedio ' + r.promedio.toFixed(1) + ' de 5' },
            iconoEstrella(true), ' ' + r.promedio.toFixed(1)),
          ' · ' + r.total + (r.total === 1 ? ' reseña' : ' reseñas'));
      } else {
        resumenVendedor.textContent = 'Sin reseñas todavía';
      }
      pintarBotonResena();
    })
    .catch(() => { resumenVendedor.textContent = ''; });

  let actual = 0;
  const grande = h('img', { class: 'grande', decoding: 'async' });
  const contador = h('span', { class: 'contador', role: 'status' }, '');
  const minis = p.fotos.map((f, i) => h('button', {
    type: 'button', class: 'mini', 'aria-pressed': String(i === 0),
    'aria-label': 'Ver foto ' + (i + 1) + ' de ' + total,
    onclick: () => mostrarFoto(i),
  }, h('img', { src: urlFoto(f.url), alt: '', loading: 'lazy', decoding: 'async' })));

  function mostrarFoto(i) {
    actual = i;
    grande.src = urlFoto(p.fotos[i].url);
    grande.alt = 'Foto ' + (i + 1) + ' de ' + total + ' de ' + p.titulo;
    minis.forEach((b, j) => b.setAttribute('aria-pressed', String(j === i)));
    contador.textContent = (i + 1) + ' / ' + total;
  }
  if (total) mostrarFoto(0);

  const galeria = total
    ? h('figure', { class: 'galeria' + (disponible ? '' : ' vendida') },
        grande,
        h('figcaption', { class: 'contador-fila' }, contador),
        total > 1 ? h('div', {
          class: 'miniaturas', role: 'group', 'aria-label': 'Fotos de la publicación',
          onkeydown: (ev) => {
            if (ev.key !== 'ArrowLeft' && ev.key !== 'ArrowRight') return;
            ev.preventDefault();
            const siguiente = ev.key === 'ArrowRight' ? (actual + 1) % total : (actual - 1 + total) % total;
            mostrarFoto(siguiente);
            minis[siguiente].focus();
          },
        }, minis) : null)
    : h('div', { class: 'sin-fotos' }, h('p', { class: 'sin-fotos__titulo' }, 'Sin fotos'),
        h('p', { class: 'sin-fotos__nota' }, 'El vendedor no subió imágenes de este artículo.'));

  const zonaContacto = h('div', { class: 'zona-contacto' });
  const btnContacto = disponible ? h('button', { class: 'btn', type: 'button', onclick: () => {
    conBotonCargando(btnContacto, 'Cargando…', async () => {
      try {
        const c = await api('GET', `/api/publicaciones/${id}/contacto`);
        const medios = [];
        if (c.whatsapp) {
          medios.push(h('p', {}, h('strong', {}, 'Celular: '), h('a', { href: 'tel:+57' + soloDigitos(c.whatsapp) }, '+57 ' + formatoCel(c.whatsapp))));
          medios.push(h('p', { class: 'contacto__accion' },
            h('a', { class: 'btn sec', href: enlaceWhatsApp(c.whatsapp), target: '_blank', rel: 'noopener' }, 'Abrir WhatsApp'),
            h('span', { class: 'meta' }, 'Se abre en una pestaña nueva.')));
        }
        if (c.correo) medios.push(h('p', {}, h('strong', {}, 'Correo: '), h('a', { href: 'mailto:' + c.correo }, c.correo)));
        if (!medios.length) medios.push(h('p', {}, 'El vendedor no registró datos de contacto.'));
        zonaContacto.replaceChildren(h('div', { class: 'contacto' },
          h('strong', { class: 'contacto__titulo' }, 'Contacta a ' + vend.nombre),
          ...medios,
          h('p', { class: 'confianza' }, 'Acuerden la entrega en un lugar público del campus y revisen el artículo antes de pagar.')));
        btnContacto.remove();
      } catch (e) { aviso(e.message, true); }
    });
  } }, 'Ver WhatsApp o correo del vendedor') : null;

  const estadoVendido = disponible ? null : h('div', { class: 'estado-vendido', role: 'status' },
    h('strong', {}, 'Esta publicación ya se vendió'),
    h('p', {}, 'El vendedor ya no recibe consultas por este artículo.'));

  /* El autor gestiona (pagar, editar, vender, recibo, borrar); el ADMIN solo modera. */
  const notaPago = esMia && p.estadoPago === 'PENDIENTE'
    ? h('p', { class: 'meta pago-nota', role: 'status' }, 'Publicar cuesta $3.000 (pago simulado).')
    : null;

  const pagar = esMia && p.estadoPago === 'PENDIENTE'
    ? h('button', { class: 'btn', type: 'button', onclick: (ev) => accionEn(ev.currentTarget, 'Pagando…',
        () => api('POST', `/api/publicaciones/${id}/pago`), 'Pago aprobado', () => vistaDetalle(id)) }, 'Pagar')
    : null;
  const editar = esMia && disponible
    ? h('a', { class: 'btn' + (pagar ? ' sec' : ''), href: '#/editar/' + id }, 'Editar') : null;
  const vender = esMia && disponible
    ? h('button', { class: 'btn sec', onclick: (ev) => accionEn(ev.currentTarget, 'Guardando…',
        () => api('POST', `/api/publicaciones/${id}/sold`), 'Marcada como vendida', () => vistaDetalle(id)) }, 'Marcar como vendido') : null;
  const eliminar = (esMia || esAdmin) ? h('button', { class: 'btn-texto-peligro', onclick: (ev) => {
    const pregunta = esMia ? '¿Eliminar esta publicación?' : '¿Eliminar «' + p.titulo + '» como moderador?';
    if (confirm(pregunta + ' No se puede deshacer.'))
      accionEn(ev.currentTarget, 'Eliminando…', () => api('DELETE', '/api/publicaciones/' + id),
        'Publicación eliminada', () => { location.hash = esMia ? '#/mias' : '#/'; });
  } }, 'Eliminar') : null;

  const acciones = (esMia || esAdmin) ? h('div', { class: 'acciones' },
    pagar, editar, vender,
    esMia && p.estadoPago === 'APROBADO' ? accionesRecibo(id) : null,
    eliminar) : null;

  montar(
    h('a', { class: 'volver', href: '#/' }, '← Volver al catálogo'),
    h('header', { class: 'detalle-cab' },
      h('div', { class: 'detalle-cab__texto' },
        h('h1', { class: 'detalle-titulo', tabindex: '-1' }, p.titulo),
        h('div', { class: 'detalle-estado' },
          indicadoresPublicacion(p, { articulo: true, categoria: true }))),
      h('div', { class: 'detalle-precio' + (disponible ? '' : ' vendido') }, precio(p.precio))),
    h('div', { class: 'detalle' },
      galeria,
      h('div', { class: 'panel' },
        h('h2', { class: 'detalle-sub' }, 'Descripción'),
        h('p', { class: 'detalle-desc' }, p.descripcion),
        h('h2', { class: 'detalle-sub' }, 'Vendedor'),
        h('div', { class: 'vendedor' }, avatarVendedor,
          h('div', {}, h('strong', {}, nombreVendedor), resumenVendedor,
            h('div', { class: 'meta' }, 'Publicado el ' + fecha(p.publicadaEn)))),
        btnContacto, zonaContacto, zonaResena, estadoVendido, notaPago, acciones)));
}

/* ---------- reseñas del vendedor ---------- */
function estrellasDe(n) {
  const llenas = Math.max(0, Math.min(5, Math.round(Number(n) || 0)));
  return Array.from({ length: 5 }, (_, i) => iconoEstrella(i < llenas));
}

/* Selector de estrellas 1..5: radios nativos (teclado y lector de pantalla) con estrellas clicables. */
function selectorEstrellas() {
  const fila = h('div', { class: 'estrellas-picker__fila' },
    [1, 2, 3, 4, 5].flatMap((n) => [
      h('input', { type: 'radio', name: 'estrellas', id: 'estrella-' + n, value: String(n), class: 'estrella__radio' }),
      h('label', { for: 'estrella-' + n, class: 'estrella__opcion', title: n + ' de 5 estrellas' },
        h('span', { 'aria-hidden': 'true' }, iconoEstrella(true)),
        h('span', { class: 'sr-only' }, n + (n === 1 ? ' estrella' : ' estrellas'))),
    ]));
  const labels = [...fila.querySelectorAll('label')];
  const pista = h('p', { class: 'estrellas-picker__valor', 'aria-live': 'polite' }, 'Elige de 1 a 5 estrellas');
  const campo = h('fieldset', { class: 'estrellas-picker' }, h('legend', {}, 'Estrellas'), fila, pista);
  const valor = () => Number(campo.querySelector('input:checked')?.value || 0);
  const pintar = (n) => labels.forEach((l, i) => l.classList.toggle('llena', i < n));

  fila.addEventListener('mouseover', (ev) => {
    const l = ev.target.closest('label');
    if (l) pintar(labels.indexOf(l) + 1);
  });
  fila.addEventListener('mouseleave', () => pintar(valor()));
  campo.addEventListener('change', () => {
    const n = valor();
    pintar(n);
    pista.textContent = n ? n + ' de 5 estrellas' : 'Elige de 1 a 5 estrellas';
  });

  return {
    campo,
    valor,
    foco: () => (campo.querySelector('input:checked') || campo.querySelector('input'))?.focus(),
  };
}

async function vistaVendedor(usuario) {
  const enc = encodeURIComponent(usuario);
  const [perfil, datos] = await Promise.all([
    api('GET', '/api/perfil/publico?usuario=' + enc),
    api('GET', '/api/perfil/' + enc + '/resenas'),
  ]);
  const sesion = leerSesion();
  const esMio = Boolean(sesion && sesion.usuario === usuario);
  const mia = datos.resenas.find((r) => r.autor === (sesion && sesion.usuario));
  const nombreVisible = perfil.nombre || nombreCorto(usuario);
  document.title = nombreVisible + ' · UPMarket';

  const resumen = h('p', { class: 'vendedor-resumen' }, datos.total
    ? h('span', { class: 'estrellas', 'aria-label': 'Promedio ' + datos.promedio.toFixed(1) + ' de 5' },
        iconoEstrella(true), ' ' + datos.promedio.toFixed(1) + ' · ' + datos.total + (datos.total === 1 ? ' reseña' : ' reseñas'))
    : h('span', { class: 'meta' }, 'Sin reseñas todavía'));

  const lista = datos.resenas.length
    ? h('ul', { class: 'resenas' }, datos.resenas.map((r) => h('li', { class: 'resena' },
        h('div', { class: 'resena__cab' },
          h('strong', {}, r.autorNombre || nombreCorto(r.autor)),
          h('span', { class: 'estrellas', 'aria-label': r.estrellas + ' de 5 estrellas' }, estrellasDe(r.estrellas)),
          h('span', { class: 'resena__fecha' }, fecha(r.creadaEn))),
        h('p', { class: 'resena__texto' }, r.comentario))))
    : h('p', { class: 'meta' }, 'Este vendedor todavía no tiene reseñas.');

  const formulario = (!esMio && !mia) ? formularioResena(usuario, enc) : null;
  const avisoPropio = esMio ? h('p', { class: 'meta' }, 'Este es tu perfil: no puedes calificarte a ti mismo.')
    : mia ? h('p', { class: 'meta' }, 'Ya calificaste a este vendedor. Tu reseña es definitiva.') : null;

  montar(
    h('a', { class: 'volver', href: '#/' }, '← Volver al catálogo'),
    h('header', { class: 'vendedor-cab' },
      avatar(perfil.fotoUrl, nombreVisible, 'grande'),
      h('div', {}, h('h1', {}, nombreVisible), resumen)),
    h('div', { class: 'panel' },
      h('h2', { class: 'detalle-sub' }, 'Reseñas'),
      avisoPropio,
      formulario,
      lista));
}

function formularioResena(usuario, enc, alPublicar = () => vistaVendedor(usuario)) {
  const estrellas = selectorEstrellas();
  const txt = h('textarea', { id: 'comentario', required: true, minlength: 3, maxlength: 500, rows: 3,
    placeholder: 'Cuenta cómo fue tu experiencia con este vendedor' });
  const contador = h('span', { class: 'contador' }, '0/500');
  txt.addEventListener('input', () => { contador.textContent = txt.value.length + '/500'; });
  const error = h('p', { class: 'error', role: 'alert', hidden: true });

  return h('form', { class: 'resena-form', onsubmit: async (ev) => {
    ev.preventDefault();
    const estrellasElegidas = estrellas.valor();
    if (!estrellasElegidas) {
      error.textContent = 'Elige de 1 a 5 estrellas.';
      error.hidden = false; estrellas.foco(); return;
    }
    const comentario = txt.value.trim();
    if (comentario.length < 3) {
      error.textContent = 'El comentario debe tener al menos 3 caracteres.';
      error.hidden = false; txt.focus(); return;
    }
    error.hidden = true;
    const boton = ev.submitter || ev.target.querySelector('button[type="submit"]');
    const restaurar = boton ? cargandoEn(boton, 'Publicando…') : () => {};
    try {
      await api('POST', '/api/perfil/' + enc + '/resenas', { estrellas: estrellasElegidas, comentario });
      aviso('Gracias por tu reseña');
      await alPublicar();
    } catch (e) {
      error.textContent = e.message; error.hidden = false;
      restaurar();
    }
  } },
    h('h3', {}, 'Califica a este vendedor'),
    estrellas.campo,
    h('label', { for: 'comentario' }, 'Comentario'),
    txt,
    h('div', { class: 'contador-fila' }, contador),
    error,
    h('button', { class: 'btn', type: 'submit' }, 'Publicar reseña'),
    h('p', { class: 'meta', id: 'ayuda-estrellas' }, 'Solo puedes calificar una vez y la reseña no se puede editar después.'));
}

async function accion(fn, exito, despues) {
  try { await fn(); aviso(exito); await despues(); } catch (e) { aviso(e.message, true); }
}

/* ---------- estados de carga ----------
   Todo botón asíncrono muestra un spinner y un verbo mientras espera. El spinner es una animación
   CSS sobre transform; con prefers-reduced-motion queda quieto y el texto sigue explicando. */
function cargandoEn(boton, etiqueta) {
  const originales = Array.from(boton.childNodes);
  boton.disabled = true;
  boton.setAttribute('aria-busy', 'true');
  boton.replaceChildren(h('span', { class: 'spinner', 'aria-hidden': 'true' }), h('span', {}, etiqueta));
  return () => {
    if (!boton.isConnected) return;
    boton.disabled = false;
    boton.removeAttribute('aria-busy');
    boton.replaceChildren(...originales);
  };
}

async function accionEn(boton, etiqueta, fn, exito, despues) {
  const restaurar = cargandoEn(boton, etiqueta);
  try { await fn(); aviso(exito); await despues(); }
  catch (e) { aviso(e.message, true); }
  finally { restaurar(); }
}

/** Subida con progreso real: fetch no informa el avance de subida, XMLHttpRequest sí. */
function subirArchivo(archivo, ruta, alProgreso) {
  return new Promise((resolve, reject) => {
    const s = leerSesion();
    const xhr = new XMLHttpRequest();
    xhr.open('POST', API + ruta);
    if (s) xhr.setRequestHeader('Authorization', 'Bearer ' + s.token);
    xhr.upload.onprogress = (ev) => {
      if (ev.lengthComputable && alProgreso) alProgreso(Math.round((ev.loaded / ev.total) * 100));
    };
    xhr.onload = () => {
      let datos = null; try { datos = JSON.parse(xhr.responseText); } catch { /* sin cuerpo */ }
      if (xhr.status >= 200 && xhr.status < 300) resolve(datos);
      else reject(new Error(mensajeDeError(xhr.status, datos, ruta)));
    };
    xhr.onerror = () => reject(new Error('No hay conexión con el servidor'));
    xhr.onabort = () => reject(new Error('La subida se canceló'));
    const fd = new FormData();
    fd.append('archivo', archivo);
    xhr.send(fd);
  });
}

/* ---------- recibo de pago (autor y ADMIN) ----------
   El token viaja en la cabecera, nunca en la URL. El PDF llega como Blob y se revoca al cerrar. */
async function solicitarRecibo(publicacionId, descargar) {
  const s = leerSesion();
  const ruta = '/api/publicaciones/' + publicacionId + '/recibo' + (descargar ? '?descargar=true' : '');
  let r;
  try {
    r = await fetch(API + ruta, { headers: s ? { Authorization: 'Bearer ' + s.token } : {} });
  } catch { throw new Error('No hay conexión con el servidor'); }
  if (!r.ok) {
    let datos = null; try { datos = await r.json(); } catch { /* sin cuerpo */ }
    const error = new Error('No pudimos obtener el recibo. Intenta de nuevo.');
    error.estado = r.status;
    error.detalle = mensajeDeError(r.status, datos, ruta);
    throw error;
  }
  const cabecera = r.headers.get('Content-Disposition') || '';
  const encontrado = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cabecera);
  const nombre = encontrado ? decodeURIComponent(encontrado[1]) : 'recibo.pdf';
  return { blob: await r.blob(), nombre };
}

function conBotonCargando(boton, etiqueta, tarea) {
  const restaurar = cargandoEn(boton, etiqueta);
  return tarea().finally(restaurar);
}

function descargarArchivo(blob, nombre) {
  const url = URL.createObjectURL(blob);
  const enlace = h('a', { href: url, download: nombre || 'recibo.pdf' });
  document.body.append(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Diálogo nativo con el PDF en un iframe; al cerrar se libera el Blob y vuelve el foco. */
function dialogoRecibo(publicacionId, blob, origen) {
  const url = URL.createObjectURL(blob);
  const botonDescargar = h('button', { class: 'btn sec', type: 'button' }, 'Descargar');
  botonDescargar.addEventListener('click', () => {
    conBotonCargando(botonDescargar, 'Generando recibo…', async () => {
      try {
        const recibo = await solicitarRecibo(publicacionId, true);
        descargarArchivo(recibo.blob, recibo.nombre);
      } catch (e) { aviso(e.message, true); }
    });
  });
  const dialogo = h('dialog', { class: 'recibo-dialogo', 'aria-labelledby': 'recibo-titulo' },
    h('div', { class: 'recibo-dialogo__cab' },
      h('h2', { id: 'recibo-titulo' }, 'Recibo de pago'),
      h('div', { class: 'acciones recibo-dialogo__acciones' },
        botonDescargar,
        h('button', { class: 'btn sec', type: 'button', onclick: () => dialogo.close() }, 'Cerrar'))),
    h('iframe', { class: 'recibo-dialogo__marco', title: 'Recibo de pago', src: url }));
  dialogo.addEventListener('close', () => {
    URL.revokeObjectURL(url);
    dialogo.remove();
    if (origen && origen.isConnected) origen.focus();
  });
  document.body.append(dialogo);
  dialogo.showModal();
}

function botonVerRecibo(publicacionId) {
  const boton = h('button', { class: 'btn-mini', type: 'button' }, 'Ver recibo');
  boton.addEventListener('click', () => {
    conBotonCargando(boton, 'Generando recibo…', async () => {
      try {
        const recibo = await solicitarRecibo(publicacionId, false);
        if (window.matchMedia('(max-width: 639px)').matches) {
          window.location.assign(URL.createObjectURL(recibo.blob));
        } else {
          dialogoRecibo(publicacionId, recibo.blob, boton);
        }
      } catch (e) { aviso(e.message, true); }
    });
  });
  return boton;
}

function botonDescargarRecibo(publicacionId) {
  const boton = h('button', { class: 'btn-mini', type: 'button' }, 'Descargar recibo');
  boton.addEventListener('click', () => {
    conBotonCargando(boton, 'Generando recibo…', async () => {
      try {
        const recibo = await solicitarRecibo(publicacionId, true);
        descargarArchivo(recibo.blob, recibo.nombre);
      } catch (e) { aviso(e.message, true); }
    });
  });
  return boton;
}

function accionesRecibo(publicacionId) {
  return h('div', { class: 'acciones recibo-acciones' },
    botonVerRecibo(publicacionId), botonDescargarRecibo(publicacionId));
}

/* ---------- publicar y editar (HU-8, HU-12) ---------- */
async function vistaFormulario(id) {
  const [cats, actual] = await Promise.all([api('GET', '/api/categorias'), id ? api('GET', '/api/publicaciones/' + id) : null]);
  if (actual && actual.vendedor !== leerSesion().usuario) throw new Error('Solo el autor puede editar esta publicación');
  const sesion = leerSesion();
  const fotos = [];
  const titulo = h('input', { id: 't', required: true, minlength: 3, maxlength: 100, value: actual ? actual.titulo : '' });
  const desc = h('textarea', { id: 'd', required: true, minlength: 5, maxlength: 2000 }, actual ? actual.descripcion : '');
  const pr = h('input', { id: 'pr', type: 'number', inputmode: 'decimal', required: true, min: 1, max: '999999999.99', step: 'any', value: actual ? actual.precio : '' });
  const est = h('select', { id: 'e' }, h('option', { value: 'NUEVO' }, 'Nuevo'), h('option', { value: 'USADO' }, 'Usado'));
  est.value = actual ? actual.estadoArticulo : 'USADO';
  const cat = h('select', { id: 'c', required: true },
    actual ? null : h('option', { value: '', disabled: true, selected: true }, 'Elige categoría'),
    cats.map((c) => h('option', { value: c.nombre }, c.nombre)));
  if (actual) cat.value = actual.categoria;
  const previas = h('div', { class: 'previas' });
  const error = h('p', { class: 'error', id: 'error-publicar', role: 'alert', tabindex: '-1', hidden: true });
  const req = () => h('span', { class: 'req', 'aria-hidden': 'true' }, '*');

  const cTit = h('span', { class: 'contador' });
  const cDesc = h('span', { class: 'contador' });
  function refrescarCuentas() { cTit.textContent = titulo.value.length + '/100'; cDesc.textContent = desc.value.length + '/2000'; }

  let zona = null, cuenta = null, archivo = null, zonaTitulo = null, zonaAyuda = null;
  let idSubida = 0;
  const subidas = [];
  async function subirArchivos(lista) {
    for (const a of Array.from(lista)) {
      if (fotos.length + subidas.length >= 5) { aviso('Máximo 5 fotos por publicación', true); break; }
      if (!/^image\/(jpeg|png)$/.test(a.type)) { aviso(a.name + ': usa JPG o PNG', true); continue; }
      if (a.size > 5 * 1024 * 1024) { aviso(a.name + ' pesa más de 5 MB', true); continue; }
      const enCurso = { id: ++idSubida, nombre: a.name, progreso: 0 };
      subidas.push(enCurso);
      pintarPrevias();
      try {
        const foto = await subirArchivo(a, '/api/fotos', (p) => { enCurso.progreso = p; pintarPrevias(); });
        const i = subidas.indexOf(enCurso); if (i >= 0) subidas.splice(i, 1);
        fotos.push(foto);
        pintarPrevias();
      } catch (e) {
        const i = subidas.indexOf(enCurso); if (i >= 0) subidas.splice(i, 1);
        pintarPrevias();
        aviso(e.message, true);
      }
    }
  }
  function pintarPrevias() {
    previas.replaceChildren(
      ...fotos.map((f, i) => h('figure', {}, h('img', { src: urlFoto(f.url), alt: 'Foto ' + (i + 1) }),
        h('button', { type: 'button', 'aria-label': 'Quitar foto ' + (i + 1), onclick: () => { fotos.splice(i, 1); pintarPrevias(); } }, iconoQuitar()))),
      ...subidas.map((subida) => h('figure', { class: 'previa-carga' },
        h('div', { class: 'previa-carga__marco' },
          h('span', { class: 'hueso previa-carga__hueso' }),
          h('span', { class: 'carga-overlay', role: 'status' },
            h('span', { class: 'spinner', 'aria-hidden': 'true' }),
            h('span', {}, 'Subiendo imagen…'),
            h('span', { class: 'carga-overlay__pct' }, subida.progreso + '%'))))));
    if (cuenta) {
      const enCurso = subidas.length;
      const total = fotos.length + enCurso;
      cuenta.replaceChildren(h('strong', {}, total + ' de 5'),
        enCurso ? ' · subiendo ' + enCurso + (enCurso === 1 ? ' foto' : ' fotos')
          : (fotos.length === 1 ? ' · foto agregada' : ' · fotos agregadas'));
      const llena = total >= 5;
      zona.classList.toggle('llena', llena);
      archivo.disabled = llena;
      zonaTitulo.textContent = llena ? 'Máximo 5 fotos alcanzado' : 'Arrastra tus fotos aquí o elige archivos';
      zonaAyuda.textContent = llena ? 'Quita una foto para agregar otra' : 'JPG o PNG · máximo 5 MB cada una · hasta 5 fotos';
    }
    actualizarPrevia();
  }

  /* Medios de contacto: el vendedor elige teléfono, correo o ambos. */
  const telC = campoCelular('ws', perfilActual && perfilActual.telefono); const tel = telC.input;
  const correoFijo = sesion.usuario.includes('@');   // quien ingresó con un correo publica siempre con ese correo
  const mail = h('input', { id: 'mail', type: 'email', maxlength: 120, autocomplete: 'email', placeholder: 'tucorreo@ejemplo.com', value: correoFijo ? sesion.usuario : '', readonly: correoFijo, 'aria-readonly': correoFijo ? 'true' : null });
  const usarTel = h('input', { id: 'usarTel', type: 'checkbox', checked: !!(perfilActual && perfilActual.telefono) });
  const usarMail = h('input', { id: 'usarMail', type: 'checkbox', checked: true });
  const cajaTel = h('div', { class: 'campo' }, h('label', { for: 'ws' }, 'Número de celular'), telC.caja),
    cajaMail = h('div', { class: 'campo' }, h('label', { for: 'mail' }, 'Correo electrónico'), mail,
      correoFijo ? h('p', { class: 'ayuda' }, 'Es el correo con el que ingresaste; no se puede cambiar.') : null);
  const opTel = h('div', { class: 'opcion' },
    h('label', { class: 'opcion__cab', for: 'usarTel' }, usarTel,
      h('span', { class: 'opcion__texto' },
        h('span', { class: 'opcion__titulo' }, 'Celular / WhatsApp'),
        h('span', { class: 'opcion__nota' }, 'Te escriben por WhatsApp o llamada'))),
    cajaTel);
  const opMail = h('div', { class: 'opcion' },
    h('label', { class: 'opcion__cab', for: 'usarMail' }, usarMail,
      h('span', { class: 'opcion__texto' },
        h('span', { class: 'opcion__titulo' }, 'Correo'),
        h('span', { class: 'opcion__nota' }, 'Te escriben a tu correo'))),
    cajaMail);
  function refrescarContacto() {
    cajaTel.hidden = !usarTel.checked; cajaMail.hidden = !usarMail.checked;
    opTel.classList.toggle('on', usarTel.checked); opMail.classList.toggle('on', usarMail.checked);
    actualizarPrevia();
  }
  usarTel.addEventListener('change', refrescarContacto); usarMail.addEventListener('change', refrescarContacto);

  const pvFoto = h('div', { class: 'vista-previa__foto' }, 'Sin fotos todavía');
  const pvTitulo = h('p', { class: 'vista-previa__titulo texto-vacio' }, 'Título de tu artículo');
  const pvPrecio = h('p', { class: 'vista-previa__precio texto-vacio' }, 'Precio por definir');
  const pvEtiquetas = h('div', { class: 'etiquetas' });
  const pvDesc = h('p', { class: 'vista-previa__desc texto-vacio' }, 'Aquí aparecerá la descripción que escribas.');
  const pvContacto = h('div', { class: 'vista-previa__contacto' });
  const nombreVendedor = (perfilActual && perfilActual.nombre) || nombreCorto(sesion.usuario);
  const vistaPrevia = h('aside', { class: 'vista-previa', 'aria-label': 'Vista previa de la publicación' },
    h('div', { class: 'vista-previa__cab' }, h('p', {}, 'Vista previa'), h('span', { class: 'meta' }, 'Como la ve un comprador')),
    pvFoto, pvTitulo, pvPrecio, pvEtiquetas, pvDesc, pvContacto,
    h('div', { class: 'vendedor' }, avatar(perfilActual && perfilActual.fotoUrl, nombreVendedor, 'mediano'),
      h('div', {}, h('strong', { class: 'nombre-previa' }, nombreVendedor), h('span', { class: 'meta' }, 'Vendedor'))));

  function actualizarPrevia() {
    const t = titulo.value.trim();
    pvTitulo.textContent = t || 'Título de tu artículo';
    pvTitulo.classList.toggle('texto-vacio', !t);
    const n = Number(pr.value);
    pvPrecio.textContent = n > 0 ? precio(n) : 'Precio por definir';
    pvPrecio.classList.toggle('texto-vacio', !(n > 0));
    pvEtiquetas.replaceChildren(
      h('span', { class: 'etiqueta' }, est.value === 'USADO' ? 'Usado' : 'Nuevo'),
      cat.value ? h('span', { class: 'etiqueta cat' }, cat.value) : h('span', { class: 'etiqueta vendida' }, 'Sin categoría'));
    const d = desc.value.trim();
    pvDesc.textContent = d || 'Aquí aparecerá la descripción que escribas.';
    pvDesc.classList.toggle('texto-vacio', !d);
    const foto = fotos[0] || (actual && actual.fotos && actual.fotos[0]);
    pvFoto.replaceChildren(foto
      ? h('img', { src: urlFoto(foto.url), alt: t ? 'Portada de ' + t : 'Foto de portada', loading: 'lazy' })
      : 'Sin fotos todavía');
    if (actual) { pvContacto.hidden = true; return; }
    const medios = [];
    if (usarTel.checked) medios.push(h('p', {}, h('strong', {}, 'WhatsApp: '), tel.value.trim() ? '+57 ' + formatoCel(tel.value) : 'sin número'));
    if (usarMail.checked) medios.push(h('p', {}, h('strong', {}, 'Correo: '), mail.value.trim() || 'sin correo'));
    pvContacto.replaceChildren(...(medios.length ? medios : [h('p', { class: 'meta' }, 'Sin medio de contacto elegido.')]));
  }

  function iconoFoto() {
    const ns = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(ns, 'svg');
    for (const [k, v] of Object.entries({ width: '28', height: '28', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '1.6', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true' })) svg.setAttribute(k, v);
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5zM4 16l4.5-4.5 3.5 3.5 2.5-2.5L20 17M9.5 8.5h.01');
    svg.append(path);
    return svg;
  }

  function iconoQuitar() {
    const ns = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(ns, 'svg');
    for (const [k, v] of Object.entries({ width: '14', height: '14', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2.2', 'stroke-linecap': 'round', 'aria-hidden': 'true' })) svg.setAttribute(k, v);
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M6 6l12 12M18 6L6 18');
    svg.append(path);
    return svg;
  }

  if (!actual) {
    archivo = h('input', { id: 'f', type: 'file', accept: 'image/jpeg,image/png', multiple: true, class: 'subida__input',
      onchange: async (ev) => { await subirArchivos(ev.target.files); ev.target.value = ''; } });
    zona = h('label', { class: 'subida', for: 'f' }, archivo, iconoFoto(),
      zonaTitulo = h('span', { class: 'subida__titulo' }, 'Arrastra tus fotos aquí o elige archivos'),
      zonaAyuda = h('span', { class: 'subida__ayuda' }, 'JPG o PNG · máximo 5 MB cada una · hasta 5 fotos'));
    zona.addEventListener('dragover', (ev) => { ev.preventDefault(); zona.classList.add('sobre'); });
    zona.addEventListener('dragleave', () => zona.classList.remove('sobre'));
    zona.addEventListener('drop', (ev) => { ev.preventDefault(); zona.classList.remove('sobre'); if (fotos.length < 5) subirArchivos(ev.dataTransfer.files); });
    cuenta = h('p', { class: 'previas__cuenta', role: 'status' });
  }

  const etiquetaBoton = actual ? 'Guardar cambios' : 'Publicar';
  const boton = h('button', { class: 'btn', type: 'submit' }, etiquetaBoton);
  const acciones = h('div', { class: 'acciones' }, boton, h('a', { class: 'btn sec', href: actual ? '#/p/' + id : '#/' }, 'Cancelar'));
  const validables = [titulo, desc, pr, cat, tel, mail];
  const limpiarError = () => {
    error.hidden = true;
    validables.forEach((el) => {
      el.removeAttribute('aria-invalid');
      if (el.getAttribute('aria-describedby') === 'error-publicar') el.removeAttribute('aria-describedby');
    });
  };
  const form = h('form', { class: 'panel publicar__form', novalidate: true, onsubmit: async (ev) => {
    ev.preventDefault();
    limpiarError();
    const mostrar = (m, campo) => {
      error.textContent = m; error.hidden = false;
      if (campo) { campo.setAttribute('aria-invalid', 'true'); campo.setAttribute('aria-describedby', 'error-publicar'); }
      error.focus();
    };
    const base = { titulo: titulo.value.trim(), descripcion: desc.value.trim(), precio: pr.value, estado: est.value, categoria: cat.value };
    if (base.titulo.length < 3) return mostrar('El título debe tener al menos 3 caracteres', titulo);
    if (base.descripcion.length < 5) return mostrar('La descripción debe tener al menos 5 caracteres', desc);
    if (!(Number(base.precio) > 0)) return mostrar('Escribe un precio mayor que cero', pr);
    if (Number(base.precio) > 999999999.99) return mostrar('El precio máximo es $999.999.999,99', pr);
    if (!base.categoria) return mostrar('Elige una categoría', cat);
    if (!actual) {
      if (!usarTel.checked && !usarMail.checked) return mostrar('Elige al menos una forma de contacto: celular o correo');
      if (usarTel.checked && !esCelularCO(tel.value)) return mostrar(MSG_CEL, tel);
      if (usarMail.checked && !esCorreo(mail.value.trim())) return mostrar('Escribe un correo de contacto válido', mail);
    }
    const restaurar = cargandoEn(boton, actual ? 'Guardando…' : 'Publicando…');
    try {
      if (actual) { await api('PUT', '/api/publicaciones/' + id, base); aviso('Cambios guardados'); location.hash = '#/p/' + id; }
      else {
        const nueva = await api('POST', '/api/publicaciones', { ...base, fotos,
          whatsapp: usarTel.checked ? telParaApi(tel.value) : null, correo: usarMail.checked ? mail.value.trim() : null });
        aviso('¡Publicación creada!'); location.hash = '#/pagar/' + nueva.id;
      }
    } catch (e) { mostrar(e.message); }
    finally { restaurar(); }
  } },
    h('h1', {}, actual ? 'Editar publicación' : 'Publicar un artículo'),
    h('p', { class: 'publicar__sub' }, actual
      ? 'Actualiza los datos de tu publicación; la vista previa cambia mientras escribes.'
      : 'Completa los datos; la vista previa te muestra cómo la verán los compradores.'),
    h('h2', {}, 'Artículo'),
    h('p', { class: 'publicar__nota' }, 'Los campos con ', req(), ' son obligatorios.'),
    h('div', {}, h('div', { class: 'campo-cab' }, h('label', { for: 't' }, 'Título ', req()), cTit), titulo),
    h('div', {}, h('div', { class: 'campo-cab' }, h('label', { for: 'd' }, 'Descripción ', req()), cDesc), desc),
    h('div', { class: 'fila' },
      h('div', {}, h('label', { for: 'pr' }, 'Precio (COP) ', req()), pr, h('p', { class: 'ayuda' }, 'Hasta $999.999.999,99')),
      h('div', {}, h('label', { for: 'e' }, 'Estado del artículo'), est),
      h('div', {}, h('label', { for: 'c' }, 'Categoría ', req()), cat)),
    actual ? h('p', { class: 'publicar__nota' }, 'Las fotos y el contacto no se editan: para cambiarlos, elimina y vuelve a publicar.') : [
      h('h2', {}, 'Fotos'),
      h('p', { class: 'publicar__nota' }, 'Opcional. La primera foto se usa como portada en el catálogo.'),
      zona, previas, cuenta,
      h('h2', {}, 'Contacto'),
      h('p', { class: 'ayuda' }, 'Elige una o las dos formas. Solo se muestran a quien pida tu contacto mientras el artículo esté disponible.'),
      h('fieldset', { class: 'contacto-opciones' }, h('legend', { class: 'sr-only' }, 'Medios de contacto'), opTel, opMail)],
    error,
    acciones);

  titulo.addEventListener('input', () => { refrescarCuentas(); actualizarPrevia(); });
  desc.addEventListener('input', () => { refrescarCuentas(); actualizarPrevia(); });
  pr.addEventListener('input', actualizarPrevia);
  est.addEventListener('change', actualizarPrevia);
  cat.addEventListener('change', actualizarPrevia);
  tel.addEventListener('input', actualizarPrevia);
  mail.addEventListener('input', actualizarPrevia);
  refrescarCuentas();
  refrescarContacto();
  pintarPrevias();
  /* En pantallas estrechas la vista previa se revisa antes de publicar. */
  montar(h('div', { class: 'publicar' }, form, vistaPrevia));
  if (window.matchMedia('(max-width: 1020px)').matches) form.insertBefore(vistaPrevia, error);
}

/* ---------- mis publicaciones (HU-12) ---------- */
let filtroMias = 'todas';

function filaMia(p) {
  const vendida = p.estadoPublicacion === 'VENDIDA';
  const foto = p.fotos && p.fotos[0]
    ? h('img', { class: 'mias-fila__foto', src: urlFoto(p.fotos[0].url), alt: '', loading: 'lazy' })
    : h('div', { class: 'mias-fila__foto mias-fila__foto--vacia' }, 'Sin foto');
  const cuando = vendida && p.vendidaEn
    ? 'Vendida el ' + fecha(p.vendidaEn)
    : 'Publicada el ' + fecha(p.publicadaEn);
  const principal = p.estadoPago === 'PENDIENTE'
    ? h('button', { class: 'btn-mini btn-mini--primario', type: 'button', 'aria-label': 'Pagar ' + p.titulo,
        onclick: (ev) => accionEn(ev.currentTarget, 'Pagando…',
          () => api('POST', `/api/publicaciones/${p.id}/pago`), 'Pago aprobado', () => vistaMias(true)) }, 'Pagar')
    : h('a', { class: 'btn-mini btn-mini--primario', href: '#/p/' + p.id, 'aria-label': 'Ver ' + p.titulo }, 'Ver');
  return h('li', { class: 'mias-fila' + (vendida ? ' mias-fila--vendida' : '') },
    h('a', { class: 'mias-fila__principal', href: '#/p/' + p.id },
      foto,
      h('div', { class: 'mias-fila__texto' },
        h('span', { class: 'mias-fila__titulo' }, p.titulo),
        h('span', { class: 'mias-fila__meta' }, [p.categoria, cuando].join(' · ')),
        h('span', { class: 'mias-fila__estado' }, indicadoresPublicacion(p, { pago: true })))),
    h('div', { class: 'mias-fila__cifras' },
      h('span', { class: 'mias-fila__precio' }, precio(p.precio))),
    h('div', { class: 'mias-fila__acciones' },
      principal,
      !vendida ? h('button', { class: 'btn-mini', type: 'button', 'aria-label': 'Editar ' + p.titulo,
        onclick: () => { location.hash = '#/editar/' + p.id; } }, 'Editar') : null,
      !vendida ? h('button', { class: 'btn-mini', type: 'button', 'aria-label': 'Marcar como vendido ' + p.titulo,
        onclick: (ev) => accionEn(ev.currentTarget, 'Guardando…',
          () => api('POST', '/api/publicaciones/' + p.id + '/sold'), 'Publicación marcada como vendida', () => vistaMias(true)) }, 'Marcar como vendido') : null,
      p.estadoPago === 'APROBADO' ? accionesRecibo(p.id) : null,
      h('button', { class: 'btn-mini btn-mini--peligro', type: 'button', 'aria-label': 'Eliminar ' + p.titulo,
        onclick: (ev) => { if (confirm('¿Eliminar «' + p.titulo + '»? Esta acción no se puede deshacer.'))
          accionEn(ev.currentTarget, 'Eliminando…', () => api('DELETE', '/api/publicaciones/' + p.id),
            'Publicación eliminada', () => vistaMias(true)); } }, 'Eliminar')));
}

function esqueletoMias() {
  return h('ul', { class: 'mias-esqueleto', 'aria-hidden': 'true' },
    Array.from({ length: 3 }, () => h('li', {},
      h('span', { class: 'bloque bloque--foto' }),
      h('span', {}, h('span', { class: 'bloque bloque--titulo' }), h('span', { class: 'bloque bloque--meta' }), h('span', { class: 'bloque bloque--estado' })),
      h('span', { class: 'bloque bloque--precio' }))));
}

/** Un ADMIN no publica: si entra a #/publicar o #/mias se le manda al panel. */
function vistaSoloEstudiantes() {
  montar(h('div', { class: 'panel vacio' },
    h('h1', {}, 'El administrador no publica artículos.'),
    h('p', { class: 'sub' }, 'Desde el panel puedes moderar el catálogo, revisar pagos y generar reportes.'),
    h('a', { class: 'btn', href: '/admin/' }, 'Ir al panel de administración')));
}

async function vistaMias(enfocar) {
  const subtitulo = h('p', { class: 'sub', role: 'status' }, 'Cargando tus publicaciones…');
  const filtros = h('div', { class: 'mias-filtros', role: 'group', 'aria-label': 'Filtrar por estado' });
  const cuerpo = h('div', { class: 'mias-cuerpo', 'aria-busy': 'true' });
  montar(h('h1', {}, 'Mis publicaciones'), subtitulo, filtros, cuerpo);
  cuerpo.replaceChildren(esqueletoMias());

  let lista;
  try { lista = await api('GET', '/api/publicaciones/mias'); }
  catch (e) {
    subtitulo.textContent = '';
    filtros.hidden = true;
    cuerpo.setAttribute('aria-busy', 'false');
    cuerpo.replaceChildren(h('div', { class: 'panel mias-error', role: 'alert' },
      h('p', { class: 'mias-error__texto' }, 'No se pudieron cargar tus publicaciones. ' + e.message),
      h('button', { class: 'btn sec', type: 'button', onclick: () => vistaMias() }, 'Reintentar')));
    return;
  }

  const disponibles = lista.filter((p) => p.estadoPublicacion === 'DISPONIBLE');
  const vendidas = lista.filter((p) => p.estadoPublicacion === 'VENDIDA');
  subtitulo.hidden = lista.length > 0;
  subtitulo.textContent = lista.length ? '' : '0 publicaciones';

  const opciones = [
    { id: 'todas', texto: 'Todas', items: lista },
    { id: 'disponibles', texto: 'Disponibles', items: disponibles },
    { id: 'vendidas', texto: 'Vendidas', items: vendidas },
  ];
  const botones = opciones.map((o) => h('button', { class: 'chip', type: 'button', onclick: () => { filtroMias = o.id; pintar(); } },
    o.texto, h('span', { class: 'cuenta' }, String(o.items.length))));
  filtros.replaceChildren(...botones);
  filtros.hidden = !lista.length;

  function pintar() {
    opciones.forEach((o, i) => botones[i].setAttribute('aria-pressed', String(filtroMias === o.id)));
    cuerpo.setAttribute('aria-busy', 'false');
    const items = (opciones.find((o) => o.id === filtroMias) || opciones[0]).items;
    if (items.length) { cuerpo.replaceChildren(h('ul', { class: 'mias-lista' }, items.map(filaMia))); return; }
    if (!lista.length) {
      cuerpo.replaceChildren(h('div', { class: 'mias-vacio' },
        h('p', { class: 'mias-vacio__titulo' }, 'Todavía no has publicado nada.'),
        h('p', { class: 'mias-vacio__nota' }, 'Publica lo que ya no uses y adminístralo desde aquí: edita el precio, márcalo como vendido o elimínalo.'),
        h('a', { class: 'btn', href: '#/publicar' }, 'Publicar un artículo')));
      return;
    }
    cuerpo.replaceChildren(h('div', { class: 'mias-vacio' },
      h('p', { class: 'mias-vacio__titulo' }, filtroMias === 'vendidas' ? 'No tienes publicaciones vendidas.' : 'No tienes publicaciones disponibles.'),
      h('button', { class: 'btn sec', type: 'button', onclick: () => { filtroMias = 'todas'; pintar(); } }, 'Ver todas')));
  }
  pintar();
  if (enfocar) {
    const h1 = $app().querySelector('h1');
    if (h1) { h1.setAttribute('tabindex', '-1'); h1.focus({ preventScroll: true }); }
  }
}

/* ---------- perfil y ajustes de cuenta ---------- */
function esqueletoPerfil() {
  return h('div', { class: 'perfil-esqueleto', 'aria-hidden': 'true' },
    h('div', { class: 'avatar esqueleto-avatar' }),
    h('div', { class: 'perfil-esqueleto__campos' },
      h('span', { class: 'hueso' }),
      h('span', { class: 'hueso corto' }),
      h('span', { class: 'hueso' }),
      h('span', { class: 'hueso mini' })));
}

async function vistaPerfil() {
  const s = leerSesion();
  let p;
  montar(h('h1', {}, 'Mi perfil'), h('p', { class: 'sub' }, 'Cargando tu perfil…'), esqueletoPerfil());
  try { p = await api('GET', '/api/perfil'); }
  catch (e) {
    montar(h('h1', {}, 'Mi perfil'),
      h('div', { class: 'panel' },
        h('p', {}, e.message || 'No pudimos cargar tu perfil.'),
        h('div', { class: 'acciones' },
          h('button', { class: 'btn', type: 'button', onclick: () => vistaPerfil() }, 'Reintentar'),
          h('a', { class: 'btn sec', href: '#/' }, 'Volver al catálogo'))));
    return;
  }
  perfilActual = p;
  let fotoUrl = p.fotoUrl;

  const nombre = h('input', { id: 'pn', required: true, minlength: 2, maxlength: 60, value: p.nombre, autocomplete: 'name', 'aria-describedby': 'perfil-error' });
  const telC = campoCelular('pt', p.telefono); const tel = telC.input;
  tel.setAttribute('aria-describedby', 'ayuda-cel perfil-error');
  const error = h('p', { class: 'error', id: 'perfil-error', role: 'alert', hidden: true });
  const ocultarError = () => {
    error.hidden = true;
    nombre.removeAttribute('aria-invalid'); tel.removeAttribute('aria-invalid');
  };

  const foto = h('div', { class: 'perfil-foto' });
  const previaFoto = h('div');
  const previaNombre = h('span', { class: 'nombre-previa' }, p.nombre);
  const previaTel = h('span', { class: 'meta' });
  let subiendo = null;
  const errorFoto = h('p', { class: 'error', role: 'alert', tabindex: '-1', hidden: true });
  const nombreVisible = () => nombre.value.trim() || p.nombre;
  const pintarFoto = () => {
    const retrato = avatar(fotoUrl, nombreVisible(), 'grande', 'Foto de perfil de ' + nombreVisible());
    foto.replaceChildren(subiendo == null ? retrato : h('span', { class: 'avatar-envoltura' }, retrato,
      h('span', { class: 'carga-overlay carga-overlay--avatar', role: 'status' },
        h('span', { class: 'spinner', 'aria-hidden': 'true' }),
        h('span', {}, 'Subiendo imagen…'),
        h('span', { class: 'carga-overlay__pct' }, subiendo + '%'))));
    previaFoto.replaceChildren(avatar(fotoUrl, nombreVisible(), 'mediano'));
    previaNombre.textContent = nombreVisible();
    const d = soloDigitos(tel.value);
    previaTel.textContent = d ? '+57 ' + formatoCel(d) : 'Sin celular público';
  };
  pintarFoto();
  nombre.addEventListener('input', () => { ocultarError(); pintarFoto(); });
  tel.addEventListener('input', () => { ocultarError(); pintarFoto(); });

  const subir = h('input', { id: 'pf', type: 'file', accept: 'image/jpeg,image/png', hidden: true, onchange: async (ev) => {
    const a = ev.target.files[0]; ev.target.value = '';
    if (!a) return;
    errorFoto.hidden = true;
    if (a.size > 5 * 1024 * 1024) {
      errorFoto.textContent = 'La foto pesa más de 5 MB.';
      errorFoto.hidden = false; errorFoto.focus();
      return;
    }
    const anterior = fotoUrl;
    subiendo = 0; subir.disabled = true; pintarFoto();
    try {
      const r = await subirArchivo(a, '/api/perfil/foto', (progreso) => { subiendo = progreso; pintarFoto(); });
      fotoUrl = r.fotoUrl; perfilActual = r; subiendo = null; pintarFoto(); pintarNav(); aviso('Foto de perfil actualizada');
    } catch (e) {
      fotoUrl = anterior; subiendo = null; pintarFoto();
      errorFoto.textContent = e.message || 'No pudimos subir la foto. Intenta de nuevo.';
      errorFoto.hidden = false; errorFoto.focus();
    } finally { subir.disabled = false; }
  } });

  const confirmacion = h('input', { id: 'conf', autocomplete: 'off', placeholder: 'ELIMINAR', 'aria-describedby': 'ayuda-conf' });
  const borrar = h('button', { class: 'btn peligro', type: 'button', disabled: true, onclick: async (ev) => {
    const restaurar = cargandoEn(ev.currentTarget, 'Eliminando…');
    try {
      await api('DELETE', '/api/perfil');
      localStorage.removeItem(CLAVE_TOKEN); perfilActual = null; location.hash = '#/login';
      aviso('Tu cuenta fue eliminada');
    } catch (e) {
      aviso(e.message, true); restaurar();
    }
  } }, 'Eliminar mi cuenta definitivamente');
  confirmacion.addEventListener('input', () => { borrar.disabled = confirmacion.value.trim() !== 'ELIMINAR'; });

  const guardar = h('button', { class: 'btn', type: 'submit' }, 'Guardar cambios');
  const mostrarError = (mensaje, campo) => {
    error.textContent = mensaje; error.hidden = false;
    nombre.removeAttribute('aria-invalid'); tel.removeAttribute('aria-invalid');
    if (campo) { campo.setAttribute('aria-invalid', 'true'); campo.focus(); }
  };

  montar(h('h1', {}, 'Mi perfil'),
    h('p', { class: 'sub' }, 'Personaliza cómo te ven los demás estudiantes.'),
    h('div', { class: 'ajustes' },
      h('section', { class: 'ajustes-seccion' },
        h('div', { class: 'ajustes-info' },
          h('h2', {}, 'Perfil público'),
          h('p', {}, 'Tu nombre y tu foto aparecen en cada publicación. El celular solo lo ven quienes piden tu contacto.'),
          h('div', { class: 'perfil-previa' },
            h('p', { class: 'perfil-previa-titulo' }, 'Así te ven los demás'),
            h('div', { class: 'vendedor' }, previaFoto, h('div', {}, previaNombre, previaTel)))),
        h('form', { class: 'ajustes-panel', novalidate: true, onsubmit: async (ev) => {
          ev.preventDefault(); ocultarError();
          if (nombre.value.trim().length < 2) { mostrarError('Escribe tu nombre', nombre); return; }
          if (soloDigitos(tel.value) && !esCelularCO(tel.value)) { mostrarError(MSG_CEL, tel); return; }
          const restaurar = cargandoEn(guardar, 'Guardando…');
          try {
            perfilActual = await api('PUT', '/api/perfil', { nombre: nombre.value.trim(), telefono: telParaApi(tel.value), fotoUrl });
            fotoUrl = perfilActual.fotoUrl; error.hidden = true; pintarFoto(); pintarNav(); aviso('Perfil actualizado');
          } catch (e) { mostrarError(e.message); }
          finally { restaurar(); }
        } },
          h('div', { class: 'perfil-cab' }, foto,
            h('div', { class: 'acciones', style: 'margin:0' },
              h('button', { class: 'btn sec', type: 'button', onclick: () => subir.click() }, 'Cambiar foto'),
              h('button', { class: 'btn sec', type: 'button', onclick: (ev) => conBotonCargando(ev.currentTarget, 'Eliminando…', async () => {
                try { perfilActual = await api('DELETE', '/api/perfil/foto'); fotoUrl = perfilActual.fotoUrl; pintarFoto(); pintarNav(); aviso('Foto de perfil eliminada'); }
                catch (e) { aviso(e.message, true); }
              }) }, 'Quitar foto'), subir)),
          h('p', { class: 'ayuda' }, 'JPG o PNG, hasta 5 MB. La foto se aplica de inmediato.'),
          errorFoto,
          h('label', { for: 'pn' }, 'Nombre'), nombre,
          h('label', { for: 'pc' }, 'Correo'),
          h('input', { id: 'pc', value: s.usuario, readonly: true, 'aria-readonly': 'true', 'aria-describedby': 'ayuda-correo' }),
          h('p', { class: 'ayuda', id: 'ayuda-correo' }, 'Es tu usuario en UPMarket; no se puede cambiar aquí.'),
          h('label', { for: 'pt' }, 'Celular ', h('small', {}, '(opcional)')), telC.caja,
          h('p', { class: 'ayuda', id: 'ayuda-cel' }, 'Solo se muestra a quien pida tu contacto en una publicación.'),
          error,
          h('div', { class: 'acciones', style: 'justify-content:flex-end' }, guardar))),
      h('section', { class: 'ajustes-seccion' },
        h('div', { class: 'ajustes-info' },
          h('h2', {}, 'Sesión y contraseña'),
          h('p', {}, 'La contraseña la administra el servicio de autenticación (AuthCore), que todavía no ofrece cambiarla desde aquí.')),
        h('div', { class: 'ajustes-panel' },
          h('p', { class: 'ayuda' }, 'Puedes cerrar sesión en este dispositivo cuando quieras.'),
          h('div', { class: 'acciones' }, h('button', { class: 'btn sec', type: 'button', onclick: cerrarSesion }, 'Cerrar sesión')))),
      h('section', { class: 'ajustes-seccion' },
        h('div', { class: 'ajustes-info' },
          h('h2', {}, 'Eliminar cuenta'),
          h('p', {}, 'Se borrarán todas tus publicaciones, tus fotos y tu perfil. No se puede deshacer. Tu correo no podrá volver a registrarse.')),
        h('div', { class: 'ajustes-panel' },
          h('label', { for: 'conf' }, 'Escribe ELIMINAR para confirmar'), confirmacion,
          h('p', { class: 'ayuda', id: 'ayuda-conf' }, 'Esta acción elimina tu cuenta de forma definitiva.'),
          h('div', { class: 'acciones' }, borrar)))));
}
