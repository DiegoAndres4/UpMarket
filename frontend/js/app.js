/* UPMarket · frontend sin dependencias ni paso de compilación.
 * Todo se llama por rutas relativas (/api/...): nginx reparte /api/auth a AuthCore y el resto al servicio de dominio.
 * El texto de los usuarios se inserta SIEMPRE con textContent (nunca innerHTML), así no hay XSS. */
'use strict';

const API = '';           // mismo origen. Para desarrollo local se puede cambiar (ver README).
const CLAVE_TOKEN = 'upmarket.token';
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
function montar(...nodos) { $app().replaceChildren(...nodos.flat()); window.scrollTo(0, 0); }
function aviso(texto, mal) {
  const a = document.getElementById('aviso');
  a.textContent = texto; a.className = 'aviso' + (mal ? ' mal' : ''); a.hidden = false;
  clearTimeout(aviso.t); aviso.t = setTimeout(() => { a.hidden = true; }, 4500);
}
const pesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const precio = (n) => pesos.format(Number(n));
const fecha = (iso) => new Date(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' });

/* Validaciones (el servidor vuelve a validar todo; esto solo da el aviso antes). */
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const MSG_CEL = 'Ingresa un celular colombiano completo: 10 dígitos que empiecen por 3 (ej. 300 123 4567)';
const limpiarTel = (t) => (t || '').replace(/[\s-]/g, '');
const esCelularCO = (t) => /^(\+?57)?3\d{9}$/.test(limpiarTel(t));
const esCorreo = (c) => c.length <= 120 && EMAIL_RE.test(c);
const nombreCorto = (usuario) => String(usuario || '').split('@')[0];
function enlaceWhatsApp(t) {
  let d = limpiarTel(t).replace(/^\+/, '');
  if (!d.startsWith('57')) d = '57' + d;
  return 'https://wa.me/' + d;
}
function avatar(url, nombre, clase) {
  const inicial = (String(nombre || '?').trim()[0] || '?').toUpperCase();
  return url ? h('img', { class: 'avatar ' + (clase || ''), src: API + url, alt: '' })
             : h('span', { class: 'avatar ' + (clase || ''), 'aria-hidden': 'true' }, inicial);
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
  if (!r.ok) throw new Error(mensajeDeError(r.status, datos, ruta));
  return datos;
}
function mensajeDeError(estado, d, ruta) {
  if (d && Array.isArray(d.detalles) && d.detalles.length) return d.detalles.join('. ');
  if (d && d.mensaje) return d.mensaje;
  if (d && d.message) return d.message;
  if (d && d.errors) return Object.values(d.errors).join('. ');
  if (ruta === '/api/auth/register' && estado === 409) return 'Ese correo ya está registrado';
  return ({ 401: 'Correo o contraseña incorrectos', 403: 'No tienes permiso para esto', 404: 'No se encontró',
            409: 'Conflicto: intenta de nuevo' })[estado] || 'Ocurrió un error (' + estado + ')';
}

/* ---------- navegación ---------- */
function pintarNav() {
  const s = leerSesion(); const nav = document.getElementById('nav'); const ruta = location.hash || '#/';
  nav.classList.remove('abierto');
  document.getElementById('menu').setAttribute('aria-expanded', 'false');
  const enlace = (href, texto, clase) => h('a', { href, class: (ruta === href ? 'activo ' : '') + (clase || '') }, texto);
  if (!s) { nav.replaceChildren(enlace('#/login', 'Ingresar'), enlace('#/registro', 'Crear cuenta')); return; }
  const nombre = perfilActual ? perfilActual.nombre : nombreCorto(s.usuario);
  nav.replaceChildren(...[
    enlace('#/', 'Catálogo'), enlace('#/publicar', 'Publicar', 'cta'), enlace('#/mias', 'Mis publicaciones'),
    s.esAdmin ? enlace('#/categorias', 'Categorías') : null,
    h('a', { href: '#/perfil', class: ruta === '#/perfil' ? 'activo' : '' },
      avatar(perfilActual && perfilActual.fotoUrl, nombre, ''), 'Mi perfil'),
    h('button', { onclick: cerrarSesion }, 'Salir')].filter(Boolean));
}
document.getElementById('menu').addEventListener('click', () => {
  const nav = document.getElementById('nav'); const abierto = nav.classList.toggle('abierto');
  document.getElementById('menu').setAttribute('aria-expanded', String(abierto));
});
const rutas = [
  [/^#\/login$/, vistaLogin], [/^#\/registro$/, vistaRegistro], [/^#\/?$/, vistaCatalogo],
  [/^#\/publicar$/, () => vistaFormulario(null)], [/^#\/mias$/, vistaMias], [/^#\/categorias$/, vistaCategorias],
  [/^#\/perfil$/, vistaPerfil],
  [/^#\/p\/(\d+)$/, (m) => vistaDetalle(m[1])], [/^#\/editar\/(\d+)$/, (m) => vistaFormulario(m[1])],
];
async function enrutar() {
  const hash = location.hash || '#/';
  const publica = hash === '#/login' || hash === '#/registro';
  pintarNav();
  if (!publica && !leerSesion()) { location.hash = '#/login'; return; }
  if (!publica && !perfilActual) await cargarPerfil();
  for (const [patron, vista] of rutas) {
    const m = hash.match(patron);
    if (m) {
      try { await vista(m); }
      catch (e) { montar(h('div', { class: 'panel vacio' }, h('p', {}, e.message), h('a', { class: 'btn sec', href: '#/' }, 'Volver al catálogo'))); }
      return;
    }
  }
  location.hash = '#/';
}
window.addEventListener('hashchange', enrutar);
window.addEventListener('load', enrutar);

/* ---------- ingreso y registro (AuthCore: el nombre de usuario ES el correo) ---------- */
function campoClave(id, etiqueta, ayuda) {
  return [h('label', { for: id }, etiqueta, ayuda ? h('small', {}, ' ' + ayuda) : null),
          h('input', { id, type: 'password', required: true, minlength: 6, maxlength: 72, autocomplete: id === 'clave' ? 'current-password' : 'new-password' })];
}
async function iniciarSesion(correo, clave) {
  const r = await api('POST', '/api/auth/login', { username: correo, password: clave });
  localStorage.setItem(CLAVE_TOKEN, r.token);
  try { perfilActual = await api('GET', '/api/perfil'); }
  catch (e) { localStorage.removeItem(CLAVE_TOKEN); perfilActual = null; throw e; }
  location.hash = '#/';
}
function vistaLogin() {
  const error = h('p', { class: 'error', hidden: true });
  const correo = h('input', { id: 'correo', type: 'text', inputmode: 'email', autocapitalize: 'none', spellcheck: 'false', required: true, autocomplete: 'username', maxlength: 120, placeholder: 'tucorreo@ejemplo.com' });
  const [lblClave, clave] = campoClave('clave', 'Contraseña');
  const boton = h('button', { class: 'btn', type: 'submit' }, 'Ingresar');
  montar(h('form', { class: 'panel centrado', onsubmit: async (ev) => {
    ev.preventDefault(); error.hidden = true; boton.disabled = true;
    try { await iniciarSesion(correo.value.trim().toLowerCase(), clave.value); }
    catch (e) { error.textContent = e.message; error.hidden = false; }
    finally { boton.disabled = false; }
  } },
    h('h1', {}, 'Ingresar a UPMarket'),
    h('label', { for: 'correo' }, 'Correo'), correo, lblClave, clave,
    error, h('div', { class: 'acciones' }, boton),
    h('p', { class: 'meta' }, '¿No tienes cuenta? ', h('a', { href: '#/registro' }, 'Créala aquí'))));
  correo.focus();
}
function vistaRegistro() {
  const error = h('p', { class: 'error', hidden: true });
  const nombre = h('input', { id: 'nombre', required: true, minlength: 2, maxlength: 60, autocomplete: 'name' });
  const correo = h('input', { id: 'correo', type: 'email', required: true, autocomplete: 'email', maxlength: 120, placeholder: 'tucorreo@ejemplo.com' });
  const tel = h('input', { id: 'tel', type: 'tel', inputmode: 'tel', autocomplete: 'tel', maxlength: 20, placeholder: '300 123 4567' });
  const [lblClave, clave] = campoClave('clave', 'Contraseña', '(mínimo 6 caracteres)');
  const [lblClave2, clave2] = campoClave('clave2', 'Repite la contraseña');
  const boton = h('button', { class: 'btn', type: 'submit' }, 'Crear cuenta');
  montar(h('form', { class: 'panel centrado', novalidate: true, onsubmit: async (ev) => {
    ev.preventDefault(); error.hidden = true;
    const c = correo.value.trim().toLowerCase();
    const problema = nombre.value.trim().length < 2 ? 'Escribe tu nombre'
      : !esCorreo(c) ? 'Escribe un correo válido'
      : tel.value.trim() && !esCelularCO(tel.value) ? MSG_CEL
      : clave.value.length < 6 ? 'La contraseña debe tener al menos 6 caracteres'
      : clave.value !== clave2.value ? 'Las contraseñas no coinciden' : null;
    if (problema) { error.textContent = problema; error.hidden = false; return; }
    boton.disabled = true;
    try {
      await api('POST', '/api/auth/register', { username: c, password: clave.value });
      await iniciarSesion(c, clave.value);
      try { perfilActual = await api('PUT', '/api/perfil', { nombre: nombre.value.trim(), telefono: tel.value.trim() || null, fotoUrl: null }); pintarNav(); }
      catch (e) { aviso('Cuenta creada, pero no se guardó el perfil: ' + e.message, true); }
      aviso('Cuenta creada. ¡Bienvenido!');
    } catch (e) { error.textContent = e.message; error.hidden = false; }
    finally { boton.disabled = false; }
  } },
    h('h1', {}, 'Crear cuenta'),
    h('label', { for: 'nombre' }, 'Nombre'), nombre,
    h('label', { for: 'correo' }, 'Correo ', h('small', {}, '(será tu usuario; mejor el institucional)')), correo,
    h('label', { for: 'tel' }, 'Celular ', h('small', {}, '(opcional, Colombia)')), tel,
    lblClave, clave, lblClave2, clave2,
    error, h('div', { class: 'acciones' }, boton),
    h('p', { class: 'meta' }, '¿Ya tienes cuenta? ', h('a', { href: '#/login' }, 'Ingresa'))));
  nombre.focus();
}

/* ---------- catálogo y búsqueda (HU-10) ---------- */
function tarjeta(p) {
  const foto = p.fotos && p.fotos[0]
    ? h('img', { class: 'foto', src: API + p.fotos[0].url, alt: 'Foto de ' + p.titulo, loading: 'lazy' })
    : h('div', { class: 'foto' }, 'Sin foto');
  return h('a', { class: 'tarjeta item', href: '#/p/' + p.id }, foto,
    h('div', { class: 'cuerpo' },
      h('span', { class: 'precio' }, precio(p.precio)),
      h('h3', {}, p.titulo),
      h('div', {}, h('span', { class: 'etiqueta' }, p.estadoArticulo === 'USADO' ? 'Usado' : 'Nuevo'),
        p.estadoPublicacion === 'VENDIDA' ? h('span', { class: 'etiqueta vendida' }, 'Vendido') : null),
      h('span', { class: 'meta' }, p.categoria + ' · ' + nombreCorto(p.vendedor))));
}
async function vistaCatalogo() {
  const cats = await api('GET', '/api/categorias');
  const campo = (id, etiqueta, el) => h('div', {}, h('label', { for: id }, etiqueta), el);
  const q = h('input', { id: 'q', type: 'search', placeholder: 'Calculadora, libro de cálculo…', maxlength: 100 });
  const cat = h('select', { id: 'cat' }, h('option', { value: '' }, 'Todas'),
    cats.map((c) => h('option', { value: c.nombre }, `${c.nombre} (${c.disponibles})`)));
  const min = h('input', { id: 'min', type: 'number', inputmode: 'decimal', min: 0, step: 'any', placeholder: '0' });
  const max = h('input', { id: 'max', type: 'number', inputmode: 'decimal', min: 0, step: 'any', placeholder: 'Sin tope' });
  const est = h('select', { id: 'est' }, h('option', { value: '' }, 'Cualquiera'),
    h('option', { value: 'NUEVO' }, 'Nuevo'), h('option', { value: 'USADO' }, 'Usado'));
  const resultados = h('div', { class: 'rejilla' });
  const info = h('p', { class: 'sub' });

  async function buscar() {
    const ps = new URLSearchParams();
    if (q.value.trim()) ps.set('texto', q.value.trim());
    if (cat.value) ps.set('categoria', cat.value);
    if (min.value) ps.set('precioMin', min.value);
    if (max.value) ps.set('precioMax', max.value);
    if (est.value) ps.set('estado', est.value);
    try {
      const lista = await api('GET', '/api/publicaciones' + (ps.toString() ? '?' + ps : ''));
      info.textContent = lista.length === 1 ? '1 publicación' : lista.length + ' publicaciones';
      resultados.replaceChildren(...(lista.length ? lista.map(tarjeta)
        : [h('div', { class: 'vacio', style: 'grid-column:1/-1' }, 'No encontramos publicaciones con esos filtros.')]));
    } catch (e) { aviso(e.message, true); }
  }
  const filtros = h('form', { class: 'tarjeta filtros', onsubmit: (ev) => { ev.preventDefault(); buscar(); } },
    h('div', { class: 'buscar' }, h('label', { for: 'q' }, 'Buscar'), q),
    campo('cat', 'Categoría', cat), campo('min', 'Precio mínimo', min), campo('max', 'Precio máximo', max),
    campo('est', 'Estado', est),
    h('button', { class: 'btn', type: 'submit' }, 'Buscar'));
  montar(h('h1', {}, 'Catálogo'), h('p', { class: 'sub' }, 'Lo que tus compañeros están vendiendo, ordenado y en un solo lugar.'),
    filtros, info, resultados);
  buscar();
}

/* ---------- detalle y contacto (HU-11) ---------- */
async function vistaDetalle(id) {
  const p = await api('GET', '/api/publicaciones/' + id);
  const s = leerSesion();
  const esMia = s && s.usuario === p.vendedor;
  const disponible = p.estadoPublicacion === 'DISPONIBLE';
  let vend = { nombre: nombreCorto(p.vendedor), fotoUrl: null };
  try { vend = await api('GET', '/api/perfil/publico?usuario=' + encodeURIComponent(p.vendedor)); } catch { /* usa el nombre corto */ }
  const grande = h('img', { class: 'grande', alt: 'Foto de ' + p.titulo, src: p.fotos[0] ? API + p.fotos[0].url : '', hidden: !p.fotos.length });
  const minis = p.fotos.map((f, i) => h('img', { src: API + f.url, alt: 'Foto ' + (i + 1), class: i === 0 ? 'sel' : '',
    onclick: (ev) => { grande.src = API + f.url; minis.forEach((m) => m.classList.remove('sel')); ev.target.classList.add('sel'); } }));
  const zonaContacto = h('div');
  const btnContacto = h('button', { class: 'btn', disabled: !disponible, onclick: async () => {
    try {
      const c = await api('GET', `/api/publicaciones/${id}/contacto`);
      zonaContacto.replaceChildren(h('div', { class: 'contacto' }, h('strong', {}, 'Contacta a ' + vend.nombre),
        c.whatsapp ? h('p', {}, 'Celular / WhatsApp: ', h('a', { href: enlaceWhatsApp(c.whatsapp), target: '_blank', rel: 'noopener' }, c.whatsapp)) : null,
        c.correo ? h('p', {}, 'Correo: ', h('a', { href: 'mailto:' + c.correo }, c.correo)) : null));
      btnContacto.hidden = true;
    } catch (e) { aviso(e.message, true); }
  } }, disponible ? 'Ver contacto del vendedor' : 'Ya fue vendido');
  const acciones = esMia ? h('div', { class: 'acciones' },
    disponible ? h('a', { class: 'btn sec', href: '#/editar/' + id }, 'Editar') : null,
    disponible ? h('button', { class: 'btn sec', onclick: () => accion(() => api('POST', `/api/publicaciones/${id}/sold`), 'Marcada como vendida', () => vistaDetalle(id)) }, 'Marcar como vendido') : null,
    h('button', { class: 'btn peligro', onclick: () => { if (confirm('¿Eliminar esta publicación? No se puede deshacer.')) accion(() => api('DELETE', '/api/publicaciones/' + id), 'Publicación eliminada', () => { location.hash = '#/mias'; }); } }, 'Eliminar')) : null;

  montar(h('a', { href: '#/' }, '← Volver al catálogo'),
    h('div', { class: 'detalle', style: 'margin-top:12px' },
      h('div', { class: 'galeria' }, p.fotos.length ? grande : h('div', { class: 'foto tarjeta' }, 'Sin fotos'),
        h('div', { class: 'miniaturas' }, p.fotos.length > 1 ? minis : [])),
      h('div', { class: 'panel' },
        h('span', { class: 'precio', style: 'font-size:1.7rem' }, precio(p.precio)),
        h('h1', { style: 'margin-top:6px;overflow-wrap:anywhere' }, p.titulo),
        h('div', {}, h('span', { class: 'etiqueta' }, p.estadoArticulo === 'USADO' ? 'Usado' : 'Nuevo'),
          h('span', { class: 'etiqueta cat' }, p.categoria), !disponible ? h('span', { class: 'etiqueta vendida' }, 'Vendido') : null),
        h('p', { style: 'white-space:pre-wrap;overflow-wrap:anywhere' }, p.descripcion),
        h('div', { class: 'vendedor' }, avatar(vend.fotoUrl, vend.nombre, 'mediano'),
          h('div', {}, h('strong', {}, vend.nombre), h('div', { class: 'meta' }, 'Publicado el ' + fecha(p.publicadaEn)))),
        esMia ? null : btnContacto, zonaContacto, acciones)));
}
async function accion(fn, exito, despues) {
  try { await fn(); aviso(exito); await despues(); } catch (e) { aviso(e.message, true); }
}

/* ---------- publicar y editar (HU-8, HU-12) ---------- */
async function vistaFormulario(id) {
  const [cats, actual] = await Promise.all([api('GET', '/api/categorias'), id ? api('GET', '/api/publicaciones/' + id) : null]);
  if (actual && actual.vendedor !== leerSesion().usuario) throw new Error('Solo el autor puede editar esta publicación');
  const sesion = leerSesion();
  let fotos = [];
  const titulo = h('input', { id: 't', required: true, minlength: 3, maxlength: 100, value: actual ? actual.titulo : '' });
  const desc = h('textarea', { id: 'd', required: true, minlength: 5, maxlength: 2000 }, actual ? actual.descripcion : '');
  const pr = h('input', { id: 'pr', type: 'number', inputmode: 'decimal', required: true, min: 1, step: 'any', value: actual ? actual.precio : '' });
  const est = h('select', { id: 'e' }, h('option', { value: 'NUEVO' }, 'Nuevo'), h('option', { value: 'USADO' }, 'Usado'));
  est.value = actual ? actual.estadoArticulo : 'USADO';
  const cat = h('select', { id: 'c', required: true }, cats.map((c) => h('option', { value: c.nombre }, c.nombre)));
  if (actual) cat.value = actual.categoria;
  const previas = h('div', { class: 'previas' });
  const error = h('p', { class: 'error', hidden: true });

  function pintarPrevias() {
    previas.replaceChildren(...fotos.map((f, i) => h('figure', {}, h('img', { src: API + f.url, alt: 'Foto ' + (i + 1) }),
      h('button', { type: 'button', 'aria-label': 'Quitar foto', onclick: () => { fotos.splice(i, 1); pintarPrevias(); } }, '×'))));
  }
  const archivo = h('input', { id: 'f', type: 'file', accept: 'image/jpeg,image/png', multiple: true, onchange: async (ev) => {
    for (const a of ev.target.files) {
      if (fotos.length >= 5) { aviso('Máximo 5 fotos por publicación', true); break; }
      if (a.size > 5 * 1024 * 1024) { aviso(a.name + ' pesa más de 5 MB', true); continue; }
      const fd = new FormData(); fd.append('archivo', a);
      try { fotos.push(await api('POST', '/api/fotos', fd, true)); pintarPrevias(); } catch (e) { aviso(e.message, true); }
    }
    ev.target.value = '';
  } });

  /* Medios de contacto: el vendedor elige teléfono, correo o ambos. */
  const tel = h('input', { id: 'ws', type: 'tel', inputmode: 'tel', maxlength: 20, placeholder: '300 123 4567', value: (perfilActual && perfilActual.telefono) || '' });
  const mail = h('input', { id: 'mail', type: 'email', maxlength: 120, placeholder: 'tucorreo@ejemplo.com', value: sesion.usuario.includes('@') ? sesion.usuario : '' });
  const usarTel = h('input', { id: 'usarTel', type: 'checkbox', checked: !!(perfilActual && perfilActual.telefono) });
  const usarMail = h('input', { id: 'usarMail', type: 'checkbox', checked: true });
  const cajaTel = h('div', { class: 'campo' }, tel), cajaMail = h('div', { class: 'campo' }, mail);
  const opTel = h('div', { class: 'opcion' }, usarTel, h('div', { style: 'flex:1' }, h('label', { for: 'usarTel' }, 'Celular / WhatsApp'), cajaTel));
  const opMail = h('div', { class: 'opcion' }, usarMail, h('div', { style: 'flex:1' }, h('label', { for: 'usarMail' }, 'Correo'), cajaMail));
  function refrescarContacto() {
    cajaTel.hidden = !usarTel.checked; cajaMail.hidden = !usarMail.checked;
    opTel.classList.toggle('on', usarTel.checked); opMail.classList.toggle('on', usarMail.checked);
  }
  usarTel.addEventListener('change', refrescarContacto); usarMail.addEventListener('change', refrescarContacto);
  refrescarContacto();

  const boton = h('button', { class: 'btn', type: 'submit' }, actual ? 'Guardar cambios' : 'Publicar');
  const form = h('form', { class: 'panel', style: 'max-width:680px', novalidate: !actual, onsubmit: async (ev) => {
    ev.preventDefault(); error.hidden = true;
    const mostrar = (m) => { error.textContent = m; error.hidden = false; };
    const base = { titulo: titulo.value.trim(), descripcion: desc.value.trim(), precio: pr.value, estado: est.value, categoria: cat.value };
    if (!actual) {
      if (base.titulo.length < 3) return mostrar('El título debe tener al menos 3 caracteres');
      if (base.descripcion.length < 5) return mostrar('La descripción debe tener al menos 5 caracteres');
      if (!(Number(base.precio) > 0)) return mostrar('Escribe un precio mayor que cero');
      if (!usarTel.checked && !usarMail.checked) return mostrar('Elige al menos una forma de contacto: celular o correo');
      if (usarTel.checked && !esCelularCO(tel.value)) return mostrar(MSG_CEL);
      if (usarMail.checked && !esCorreo(mail.value.trim())) return mostrar('Escribe un correo de contacto válido');
    }
    boton.disabled = true;
    try {
      if (actual) { await api('PUT', '/api/publicaciones/' + id, base); aviso('Cambios guardados'); location.hash = '#/p/' + id; }
      else {
        const nueva = await api('POST', '/api/publicaciones', { ...base, fotos,
          whatsapp: usarTel.checked ? limpiarTel(tel.value) : null, correo: usarMail.checked ? mail.value.trim() : null });
        aviso('¡Publicación creada!'); location.hash = '#/p/' + nueva.id;
      }
    } catch (e) { mostrar(e.message); }
    finally { boton.disabled = false; }
  } },
    h('h1', {}, actual ? 'Editar publicación' : 'Publicar un artículo'),
    h('label', { for: 't' }, 'Título'), titulo,
    h('label', { for: 'd' }, 'Descripción'), desc,
    h('div', { class: 'fila' },
      h('div', {}, h('label', { for: 'pr' }, 'Precio (COP)'), pr),
      h('div', {}, h('label', { for: 'e' }, 'Estado del artículo'), est),
      h('div', {}, h('label', { for: 'c' }, 'Categoría'), cat)),
    actual ? h('p', { class: 'meta' }, 'Las fotos y el contacto no se editan; para cambiarlos, elimina y vuelve a publicar.') : [
      h('label', { for: 'f' }, 'Fotos ', h('small', {}, '(JPG o PNG, hasta 5 MB cada una, máximo 5)')), archivo, previas,
      h('h2', {}, '¿Cómo te pueden contactar?'),
      h('p', { class: 'ayuda' }, 'Elige una o las dos. Solo se muestra a quien lo pida mientras el artículo esté disponible.'),
      opTel, opMail],
    error, h('div', { class: 'acciones' }, boton, h('a', { class: 'btn sec', href: actual ? '#/p/' + id : '#/' }, 'Cancelar')));
  montar(form);
}

/* ---------- mis publicaciones (HU-12) ---------- */
async function vistaMias() {
  const lista = await api('GET', '/api/publicaciones/mias');
  montar(h('h1', {}, 'Mis publicaciones'), h('p', { class: 'sub' }, 'Entra a una para editarla, marcarla como vendida o eliminarla.'),
    lista.length ? h('div', { class: 'rejilla' }, lista.map(tarjeta))
      : h('div', { class: 'panel vacio' }, h('p', {}, 'Aún no has publicado nada.'), h('a', { class: 'btn', href: '#/publicar' }, 'Publicar un artículo')));
}

/* ---------- perfil y ajustes de cuenta ---------- */
async function vistaPerfil() {
  const s = leerSesion();
  const p = await api('GET', '/api/perfil');
  perfilActual = p;
  let fotoUrl = p.fotoUrl;
  const nombre = h('input', { id: 'pn', required: true, minlength: 2, maxlength: 60, value: p.nombre });
  const tel = h('input', { id: 'pt', type: 'tel', inputmode: 'tel', maxlength: 20, placeholder: '300 123 4567', value: p.telefono || '' });
  const error = h('p', { class: 'error', hidden: true });
  const foto = h('div');
  const pintarFoto = () => foto.replaceChildren(avatar(fotoUrl, nombre.value || p.nombre, 'grande'));
  pintarFoto();
  const subir = h('input', { id: 'pf', type: 'file', accept: 'image/jpeg,image/png', hidden: true, onchange: async (ev) => {
    const a = ev.target.files[0]; ev.target.value = '';
    if (!a) return;
    if (a.size > 5 * 1024 * 1024) { aviso('La foto pesa más de 5 MB', true); return; }
    const fd = new FormData(); fd.append('archivo', a);
    try { const r = await api('POST', '/api/fotos', fd, true); fotoUrl = r.url; pintarFoto(); aviso('Foto lista: pulsa Guardar cambios'); }
    catch (e) { aviso(e.message, true); }
  } });
  const confirmacion = h('input', { id: 'conf', autocomplete: 'off', placeholder: 'ELIMINAR' });
  const borrar = h('button', { class: 'btn peligro lleno', type: 'button', disabled: true, onclick: async () => {
    borrar.disabled = true;
    try {
      await api('DELETE', '/api/perfil');
      localStorage.removeItem(CLAVE_TOKEN); perfilActual = null; location.hash = '#/login';
      aviso('Tu cuenta fue eliminada');
    } catch (e) { aviso(e.message, true); borrar.disabled = false; }
  } }, 'Eliminar mi cuenta definitivamente');
  confirmacion.addEventListener('input', () => { borrar.disabled = confirmacion.value.trim() !== 'ELIMINAR'; });

  const guardar = h('button', { class: 'btn', type: 'submit' }, 'Guardar cambios');
  montar(h('h1', {}, 'Mi perfil'), h('p', { class: 'sub' }, 'Personaliza cómo te ven los demás estudiantes.'),
    h('form', { class: 'panel', style: 'max-width:640px', novalidate: true, onsubmit: async (ev) => {
      ev.preventDefault(); error.hidden = true;
      if (nombre.value.trim().length < 2) { error.textContent = 'Escribe tu nombre'; error.hidden = false; return; }
      if (tel.value.trim() && !esCelularCO(tel.value)) { error.textContent = MSG_CEL; error.hidden = false; return; }
      guardar.disabled = true;
      try {
        perfilActual = await api('PUT', '/api/perfil', { nombre: nombre.value.trim(), telefono: tel.value.trim() || null, fotoUrl });
        fotoUrl = perfilActual.fotoUrl; pintarNav(); aviso('Perfil actualizado');
      } catch (e) { error.textContent = e.message; error.hidden = false; }
      finally { guardar.disabled = false; }
    } },
      h('div', { class: 'perfil-cab' }, foto,
        h('div', { class: 'acciones', style: 'margin:0' },
          h('button', { class: 'btn sec', type: 'button', onclick: () => subir.click() }, 'Cambiar foto'),
          h('button', { class: 'btn sec', type: 'button', onclick: () => { fotoUrl = null; pintarFoto(); } }, 'Quitar foto'), subir)),
      h('label', { for: 'pn' }, 'Nombre'), nombre,
      h('label', { for: 'pc' }, 'Correo ', h('small', {}, '(es tu usuario y no se puede cambiar)')),
      h('input', { id: 'pc', value: s.usuario, disabled: true }),
      h('label', { for: 'pt' }, 'Celular ', h('small', {}, '(opcional, Colombia; solo lo ven quienes pidan tu contacto si lo eliges al publicar)')), tel,
      error, h('div', { class: 'acciones' }, guardar)),
    h('div', { class: 'panel', style: 'max-width:640px;margin-top:16px' },
      h('h2', { style: 'margin-top:0' }, 'Sesión y contraseña'),
      h('p', { class: 'ayuda' }, 'La contraseña la administra el servicio de autenticación (AuthCore), que todavía no ofrece cambiarla desde aquí.'),
      h('div', { class: 'acciones' }, h('button', { class: 'btn sec', type: 'button', onclick: cerrarSesion }, 'Cerrar sesión'))),
    h('div', { class: 'panel zona-peligro', style: 'max-width:640px' },
      h('h2', { style: 'margin-top:0;color:var(--rojo-osc)' }, 'Eliminar cuenta'),
      h('p', {}, 'Se borrarán todas tus publicaciones, tus fotos y tu perfil. No se puede deshacer. Tu correo no podrá volver a registrarse.'),
      h('label', { for: 'conf' }, 'Escribe ELIMINAR para confirmar'), confirmacion,
      h('div', { class: 'acciones' }, borrar)));
}

/* ---------- categorías (HU-9, solo ADMIN) ---------- */
async function vistaCategorias() {
  if (!leerSesion().esAdmin) throw new Error('Solo un administrador puede gestionar categorías');
  const cats = await api('GET', '/api/categorias');
  const nombre = h('input', { id: 'n', required: true, maxlength: 40, placeholder: 'Ej. Deportes' });
  const error = h('p', { class: 'error', hidden: true });
  montar(h('h1', {}, 'Categorías'),
    h('form', { class: 'panel', style: 'max-width:520px', onsubmit: async (ev) => {
      ev.preventDefault(); error.hidden = true;
      try { await api('POST', '/api/categorias', { nombre: nombre.value.trim() }); aviso('Categoría creada'); await vistaCategorias(); }
      catch (e) { error.textContent = e.message; error.hidden = false; }
    } }, h('label', { for: 'n' }, 'Nueva categoría'), nombre, error, h('div', { class: 'acciones' }, h('button', { class: 'btn', type: 'submit' }, 'Crear'))),
    h('h2', {}, 'Existentes'),
    h('div', { class: 'panel', style: 'overflow-x:auto' }, h('table', {}, h('tr', {}, h('th', {}, 'Nombre'), h('th', {}, 'Disponibles')),
      cats.map((c) => h('tr', {}, h('td', {}, c.nombre), h('td', {}, c.disponibles))))));
}
