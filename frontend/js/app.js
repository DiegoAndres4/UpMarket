/* UPMarket · frontend sin dependencias ni paso de compilación.
 * Todo se llama por rutas relativas (/api/...): nginx reparte /api/auth a AuthCore y el resto al servicio de dominio.
 * El texto de los usuarios se inserta SIEMPRE con textContent (nunca innerHTML), así no hay XSS. */
'use strict';

const API = '';           // mismo origen. Para desarrollo local se puede cambiar (ver README).
const CLAVE_TOKEN = 'upmarket.token';

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
  clearTimeout(aviso.t); aviso.t = setTimeout(() => { a.hidden = true; }, 4000);
}
const pesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const precio = (n) => pesos.format(Number(n));
const fecha = (iso) => new Date(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' });

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
function cerrarSesion() { localStorage.removeItem(CLAVE_TOKEN); location.hash = '#/login'; }

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
  if (r.status === 401 && !ruta.startsWith('/api/auth/')) {
    localStorage.removeItem(CLAVE_TOKEN); location.hash = '#/login';
    throw new Error('Tu sesión venció, ingresa de nuevo');
  }
  if (r.status === 204) return null;
  let datos = null;
  try { datos = await r.json(); } catch { /* sin cuerpo */ }
  if (!r.ok) throw new Error(mensajeDeError(r.status, datos));
  return datos;
}
function mensajeDeError(estado, d) {
  if (d && Array.isArray(d.detalles) && d.detalles.length) return d.detalles.join('. ');
  if (d && d.mensaje) return d.mensaje;
  if (d && d.message) return d.message;
  if (d && d.errors) return Object.values(d.errors).join('. ');
  return ({ 401: 'Usuario o contraseña incorrectos', 403: 'No tienes permiso para esto', 404: 'No se encontró',
            409: 'Conflicto: intenta de nuevo' })[estado] || 'Ocurrió un error (' + estado + ')';
}

/* ---------- navegación ---------- */
function pintarNav() {
  const s = leerSesion(); const nav = document.getElementById('nav'); const ruta = location.hash || '#/';
  const enlace = (href, texto) => h('a', { href, class: ruta === href ? 'activo' : '' }, texto);
  if (!s) { nav.replaceChildren(enlace('#/login', 'Ingresar'), enlace('#/registro', 'Crear cuenta')); return; }
  nav.replaceChildren(...[
    enlace('#/', 'Catálogo'), enlace('#/publicar', 'Vender'), enlace('#/mias', 'Mis publicaciones'),
    s.esAdmin ? enlace('#/categorias', 'Categorías') : null,
    h('span', { class: 'usuario' }, s.usuario + (s.esAdmin ? ' (admin)' : '')),
    h('button', { onclick: cerrarSesion }, 'Salir')].filter(Boolean));
}
const rutas = [
  [/^#\/login$/, vistaLogin], [/^#\/registro$/, vistaRegistro], [/^#\/?$/, vistaCatalogo],
  [/^#\/publicar$/, () => vistaFormulario(null)], [/^#\/mias$/, vistaMias], [/^#\/categorias$/, vistaCategorias],
  [/^#\/p\/(\d+)$/, (m) => vistaDetalle(m[1])], [/^#\/editar\/(\d+)$/, (m) => vistaFormulario(m[1])],
];
async function enrutar() {
  const hash = location.hash || '#/';
  const publica = hash === '#/login' || hash === '#/registro';
  pintarNav();
  if (!publica && !leerSesion()) { location.hash = '#/login'; return; }
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

/* ---------- ingreso y registro (AuthCore) ---------- */
function formularioCuenta(titulo, textoBoton, alEnviar, pie) {
  const error = h('p', { class: 'error', hidden: true });
  const usuario = h('input', { id: 'usuario', required: true, autocomplete: 'username', maxlength: 60 });
  const clave = h('input', { id: 'clave', type: 'password', required: true, minlength: 6, autocomplete: 'current-password' });
  const boton = h('button', { class: 'btn', type: 'submit' }, textoBoton);
  const form = h('form', { class: 'panel centrado', onsubmit: async (ev) => {
    ev.preventDefault(); error.hidden = true; boton.disabled = true;
    try { await alEnviar(usuario.value.trim(), clave.value); }
    catch (e) { error.textContent = e.message; error.hidden = false; }
    finally { boton.disabled = false; }
  } },
    h('h1', {}, titulo),
    h('label', { for: 'usuario' }, 'Usuario'), usuario,
    h('label', { for: 'clave' }, 'Contraseña ', h('small', {}, '(mínimo 6 caracteres)')), clave,
    error, h('div', { class: 'acciones' }, boton), pie);
  montar(form);
  usuario.focus();
}
async function iniciarSesion(usuario, clave) {
  const r = await api('POST', '/api/auth/login', { username: usuario, password: clave });
  localStorage.setItem(CLAVE_TOKEN, r.token);
  location.hash = '#/';
}
function vistaLogin() {
  formularioCuenta('Ingresar a UPMarket', 'Ingresar', iniciarSesion,
    h('p', { class: 'meta' }, '¿No tienes cuenta? ', h('a', { href: '#/registro' }, 'Créala aquí')));
}
function vistaRegistro() {
  formularioCuenta('Crear cuenta', 'Registrarme', async (u, c) => {
    await api('POST', '/api/auth/register', { username: u, password: c });
    aviso('Cuenta creada. ¡Bienvenido!');
    await iniciarSesion(u, c);
  }, h('p', { class: 'meta' }, '¿Ya tienes cuenta? ', h('a', { href: '#/login' }, 'Ingresa')));
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
      h('div', {}, h('span', { class: 'etiqueta' + (p.estadoArticulo === 'USADO' ? ' usado' : '') }, p.estadoArticulo === 'USADO' ? 'Usado' : 'Nuevo'),
        p.estadoPublicacion === 'VENDIDA' ? h('span', { class: 'etiqueta vendida' }, 'Vendido') : null),
      h('span', { class: 'meta' }, p.categoria + ' · ' + p.vendedor)));
}
async function vistaCatalogo() {
  const cats = await api('GET', '/api/categorias');
  const p = new URLSearchParams(location.search);
  const campo = (id, etiqueta, el) => h('div', {}, h('label', { for: id }, etiqueta), el);
  const q = h('input', { id: 'q', placeholder: 'Calculadora, libro de cálculo…', maxlength: 100 });
  const cat = h('select', { id: 'cat' }, h('option', { value: '' }, 'Todas'),
    cats.map((c) => h('option', { value: c.nombre }, `${c.nombre} (${c.disponibles})`)));
  const min = h('input', { id: 'min', type: 'number', min: 0, step: 'any', placeholder: '0' });
  const max = h('input', { id: 'max', type: 'number', min: 0, step: 'any', placeholder: 'Sin tope' });
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
  const grande = h('img', { class: 'grande', alt: 'Foto de ' + p.titulo, src: p.fotos[0] ? API + p.fotos[0].url : '', hidden: !p.fotos.length });
  const minis = p.fotos.map((f, i) => h('img', { src: API + f.url, alt: 'Foto ' + (i + 1), class: i === 0 ? 'sel' : '',
    onclick: (ev) => { grande.src = API + f.url; minis.forEach((m) => m.classList.remove('sel')); ev.target.classList.add('sel'); } }));
  const zonaContacto = h('div');
  const btnContacto = h('button', { class: 'btn', disabled: !disponible, onclick: async () => {
    try {
      const c = await api('GET', `/api/publicaciones/${id}/contacto`);
      const ws = c.whatsapp ? String(c.whatsapp).replace(/\D/g, '') : '';
      zonaContacto.replaceChildren(h('div', { class: 'contacto' }, h('strong', {}, 'Contacta a ' + p.vendedor),
        c.whatsapp ? h('p', {}, 'WhatsApp: ', h('a', { href: 'https://wa.me/' + ws, target: '_blank', rel: 'noopener' }, c.whatsapp)) : null,
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
        h('h1', { style: 'margin-top:6px' }, p.titulo),
        h('div', {}, h('span', { class: 'etiqueta' + (p.estadoArticulo === 'USADO' ? ' usado' : '') }, p.estadoArticulo === 'USADO' ? 'Usado' : 'Nuevo'),
          h('span', { class: 'etiqueta' }, p.categoria), !disponible ? h('span', { class: 'etiqueta vendida' }, 'Vendido') : null),
        h('p', { style: 'white-space:pre-wrap' }, p.descripcion),
        h('p', { class: 'meta' }, `Publicado por ${p.vendedor} el ${fecha(p.publicadaEn)}`),
        esMia ? null : btnContacto, zonaContacto, acciones)));
}
async function accion(fn, exito, despues) {
  try { await fn(); aviso(exito); await despues(); } catch (e) { aviso(e.message, true); }
}

/* ---------- publicar y editar (HU-8, HU-12) ---------- */
async function vistaFormulario(id) {
  const [cats, actual] = await Promise.all([api('GET', '/api/categorias'), id ? api('GET', '/api/publicaciones/' + id) : null]);
  if (actual && actual.vendedor !== leerSesion().usuario) throw new Error('Solo el autor puede editar esta publicación');
  let fotos = [];
  const titulo = h('input', { id: 't', required: true, minlength: 3, maxlength: 100, value: actual ? actual.titulo : '' });
  const desc = h('textarea', { id: 'd', required: true, minlength: 5, maxlength: 2000 }, actual ? actual.descripcion : '');
  const pr = h('input', { id: 'pr', type: 'number', required: true, min: 1, step: 'any', value: actual ? actual.precio : '' });
  const est = h('select', { id: 'e' }, h('option', { value: 'NUEVO' }, 'Nuevo'), h('option', { value: 'USADO' }, 'Usado'));
  est.value = actual ? actual.estadoArticulo : 'USADO';
  const cat = h('select', { id: 'c', required: true }, cats.map((c) => h('option', { value: c.nombre }, c.nombre)));
  if (actual) cat.value = actual.categoria;
  const ws = h('input', { id: 'ws', type: 'tel', placeholder: '3001234567', maxlength: 20 });
  const mail = h('input', { id: 'mail', type: 'email', placeholder: 'tucorreo@unipamplona.edu.co', maxlength: 120 });
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

  const boton = h('button', { class: 'btn', type: 'submit' }, actual ? 'Guardar cambios' : 'Publicar');
  const form = h('form', { class: 'panel', style: 'max-width:680px', onsubmit: async (ev) => {
    ev.preventDefault(); error.hidden = true; boton.disabled = true;
    try {
      const base = { titulo: titulo.value.trim(), descripcion: desc.value.trim(), precio: pr.value, estado: est.value, categoria: cat.value };
      if (actual) { await api('PUT', '/api/publicaciones/' + id, base); aviso('Cambios guardados'); location.hash = '#/p/' + id; }
      else {
        const nueva = await api('POST', '/api/publicaciones', { ...base, fotos, whatsapp: ws.value.trim() || null, correo: mail.value.trim() || null });
        aviso('¡Publicación creada!'); location.hash = '#/p/' + nueva.id;
      }
    } catch (e) { error.textContent = e.message; error.hidden = false; }
    finally { boton.disabled = false; }
  } },
    h('h1', {}, actual ? 'Editar publicación' : 'Vender un artículo'),
    h('label', { for: 't' }, 'Título'), titulo,
    h('label', { for: 'd' }, 'Descripción'), desc,
    h('div', { class: 'fila' },
      h('div', {}, h('label', { for: 'pr' }, 'Precio (COP)'), pr),
      h('div', {}, h('label', { for: 'e' }, 'Estado del artículo'), est),
      h('div', {}, h('label', { for: 'c' }, 'Categoría'), cat)),
    actual ? h('p', { class: 'meta' }, 'Las fotos y el contacto no se editan; para cambiarlos, elimina y vuelve a publicar.') : [
      h('label', { for: 'f' }, 'Fotos ', h('small', {}, '(JPG o PNG, hasta 5 MB cada una, máximo 5)')), archivo, previas,
      h('p', { class: 'meta', style: 'margin-top:14px' }, 'Indica al menos una forma de contacto. Solo se muestra a quien la solicite mientras el artículo esté disponible.'),
      h('div', { class: 'fila' }, h('div', {}, h('label', { for: 'ws' }, 'WhatsApp'), ws), h('div', {}, h('label', { for: 'mail' }, 'Correo'), mail))],
    error, h('div', { class: 'acciones' }, boton, h('a', { class: 'btn sec', href: actual ? '#/p/' + id : '#/' }, 'Cancelar')));
  montar(form);
}

/* ---------- mis publicaciones (HU-12) ---------- */
async function vistaMias() {
  const lista = await api('GET', '/api/publicaciones/mias');
  montar(h('h1', {}, 'Mis publicaciones'), h('p', { class: 'sub' }, 'Entra a una para editarla, marcarla como vendida o eliminarla.'),
    lista.length ? h('div', { class: 'rejilla' }, lista.map(tarjeta))
      : h('div', { class: 'panel vacio' }, h('p', {}, 'Aún no has publicado nada.'), h('a', { class: 'btn', href: '#/publicar' }, 'Vender un artículo')));
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
    h('div', { class: 'panel' }, h('table', {}, h('tr', {}, h('th', {}, 'Nombre'), h('th', {}, 'Disponibles')),
      cats.map((c) => h('tr', {}, h('td', {}, c.nombre), h('td', {}, c.disponibles))))));
}
