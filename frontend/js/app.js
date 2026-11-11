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
function avatar(url, nombre, clase, alt) {
  const inicial = (String(nombre || '?').trim()[0] || '?').toUpperCase();
  const etiqueta = alt || '';
  return url ? h('img', { class: 'avatar ' + (clase || ''), src: API + url, alt: etiqueta })
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
  if (!r.ok) { const err = new Error(mensajeDeError(r.status, datos, ruta)); err.estado = r.status; throw err; }
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

/* ---------- navegación ---------- */
function pintarNav() {
  const s = leerSesion(); const nav = document.getElementById('nav'); const menu = document.getElementById('menu'); const ruta = location.hash || '#/';
  nav.classList.remove('abierto');
  menu.setAttribute('aria-expanded', 'false');
  // Sin sesión no hay menú: la salida a ingreso/registro vive en el pie del propio formulario.
  menu.hidden = !s;
  if (!s) { nav.replaceChildren(); nav.hidden = true; return; }
  nav.hidden = false;
  const enlace = (href, texto, clase) => h('a', { href, class: (ruta === href ? 'activo ' : '') + (clase || '') }, texto);
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
document.querySelector('.salto')?.addEventListener('click', (ev) => {
  ev.preventDefault();
  document.getElementById('app').focus();
});
const rutas = [
  [/^#\/login$/, vistaLogin], [/^#\/registro$/, vistaRegistro], [/^#\/?$/, vistaCatalogo],
  [/^#\/publicar$/, () => vistaFormulario(null)], [/^#\/mias$/, vistaMias], [/^#\/categorias$/, vistaCategorias],
  [/^#\/perfil$/, vistaPerfil],
  [/^#\/p\/(\d+)$/, (m) => vistaDetalle(m[1])], [/^#\/editar\/(\d+)$/, (m) => vistaFormulario(m[1])],
];
const TITULOS = {
  '#/': 'Catálogo', '#/login': 'Ingresar', '#/registro': 'Crear cuenta', '#/publicar': 'Publicar',
  '#/mias': 'Mis publicaciones', '#/categorias': 'Categorías', '#/perfil': 'Mi perfil',
};
function tituloDe(hash) {
  if (/^#\/p\//.test(hash)) return 'Detalle de publicación';
  if (/^#\/editar\//.test(hash)) return 'Editar publicación';
  return TITULOS[hash] || '';
}
async function enrutar() {
  const hash = location.hash || '#/';
  const publica = hash === '#/login' || hash === '#/registro';
  pintarNav();
  const titulo = tituloDe(hash);
  document.title = titulo ? titulo + ' · UPMarket' : 'UPMarket · Mercado de estudiantes';
  if (!publica && !leerSesion()) { location.hash = '#/login'; return; }
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
  location.hash = '#/';
}
function vistaLogin() {
  const error = h('p', { class: 'error', id: 'error-login', role: 'alert', hidden: true });
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
    h('aside', { class: 'acceso__marca', 'aria-label': 'UPMarket' },
      h('p', { class: 'acceso__logo' }, 'UP', h('span', {}, 'Market')),
      h('ul', { class: 'acceso__lista' },
        h('li', {}, 'Publica lo que quieres vender'),
        h('li', {}, 'Encuentra artículos de otros estudiantes'),
        h('li', {}, 'Contacta al vendedor directamente')),
      h('img', { class: 'acceso__mascota', src: 'public/mascota-upmarket.png', alt: '', width: 241, height: 360, 'aria-hidden': 'true' }),
      h('div', {},
        h('p', { class: 'acceso__lema' }, 'Compra, vende e intercambia entre estudiantes de la Universidad de Pamplona.'),
        h('p', { class: 'acceso__lugar' }, 'Villa del Rosario · Norte de Santander'))),
    h('form', { class: 'acceso__form', novalidate: true, onsubmit: async (ev) => {
      ev.preventDefault();
      const c = correo.value.trim().toLowerCase();
      const problema = !c ? 'Escribe tu correo o usuario'
        : !esUsuarioLogin(c) ? 'Escribe un correo válido o el usuario admin'
        : !clave.value ? 'Escribe tu contraseña'
        : clave.value.length < 6 ? 'La contraseña debe tener al menos 6 caracteres' : null;
      if (problema) { fallar(problema, !c || !esUsuarioLogin(c) ? correo : clave); return; }
      error.hidden = true;
      boton.disabled = true; boton.setAttribute('aria-busy', 'true'); boton.textContent = 'Ingresando…';
      try { await iniciarSesion(c, clave.value); }
      catch (e) { fallar(e.message, clave); }
      finally { boton.disabled = false; boton.removeAttribute('aria-busy'); boton.textContent = 'Ingresar'; }
    } },
      h('h1', {}, 'Ingresar a UPMarket'),
      h('p', { class: 'acceso__sub' }, 'Tus publicaciones, tus ventas y tus mensajes.'),
      h('label', { for: 'correo' }, 'Correo o usuario'), correo,
      lblClave, clave,
      h('div', { class: 'ver-clave' }, ver),
      error,
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
    h('aside', { class: 'acceso__marca', 'aria-label': 'UPMarket' },
      h('p', { class: 'acceso__logo' }, 'UP', h('span', {}, 'Market')),
      h('ul', { class: 'acceso__lista' },
        h('li', {}, 'Publica lo que quieres vender'),
        h('li', {}, 'Encuentra artículos de otros estudiantes'),
        h('li', {}, 'Contacta al vendedor directamente')),
      h('img', { class: 'acceso__mascota', src: 'public/mascota-upmarket.png', alt: '', width: 241, height: 360, 'aria-hidden': 'true' }),
      h('div', {},
        h('p', { class: 'acceso__lema' }, 'Compra, vende e intercambia entre estudiantes de la Universidad de Pamplona.'),
        h('p', { class: 'acceso__lugar' }, 'Villa del Rosario · Norte de Santander'))),
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
      boton.disabled = true; boton.setAttribute('aria-busy', 'true'); boton.textContent = 'Creando cuenta…';
      try {
        await api('POST', '/api/auth/register', { username: c, password: clave.value });
      } catch (e) {
        if (e.estado === 409) { marcar(correo, errCorreo, 'Ese correo ya está registrado. Ingresa o usa otro.'); correo.focus(); }
        else if (e.estado === 400) fallar('Hay un dato que no cumple el formato. Revísalo e intenta de nuevo.');
        else fallar(e.message);
        boton.disabled = false; boton.removeAttribute('aria-busy'); boton.textContent = 'Crear cuenta';
        return;
      }
      try { await iniciarSesion(c, clave.value); }
      catch {
        aviso('Tu cuenta se creó. Ingresa con tu correo y contraseña.', true);
        location.hash = '#/login';
        return;
      }
      try {
        perfilActual = await api('PUT', '/api/perfil', { nombre: nombre.value.trim(), telefono: telParaApi(tel.value), fotoUrl: null });
        pintarNav();
        aviso('Cuenta creada. ¡Bienvenido!');
      } catch {
        aviso('Cuenta creada. Completa tu perfil.', true);
        location.hash = '#/perfil';
      }
    } },
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

/* ---------- catálogo y búsqueda (HU-10) ---------- */
function tarjeta(p) {
  const foto = p.fotos && p.fotos[0]
    ? h('img', { class: 'foto', src: API + p.fotos[0].url, alt: '', loading: 'lazy', decoding: 'async' })
    : h('div', { class: 'foto' }, 'Sin foto');
  return h('a', { class: 'tarjeta item', href: '#/p/' + p.id }, foto,
    h('div', { class: 'cuerpo' },
      h('span', { class: 'precio' }, precio(p.precio)),
      h('h3', {}, p.titulo),
      h('div', {},
        h('span', { class: 'etiqueta' + (p.estadoArticulo === 'USADO' ? ' usado' : '') }, p.estadoArticulo === 'USADO' ? 'Usado' : 'Nuevo'),
        p.estadoPublicacion === 'VENDIDA' ? h('span', { class: 'etiqueta vendida' }, 'Vendido') : null),
      h('span', { class: 'meta' }, p.categoria + ' · ' + nombreCorto(p.vendedor))));
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
/* Sin filtros: todo lo disponible, ordenado por secciones (categorías). Con filtros: lista plana con «Mostrar más». */
async function vistaCatalogo() {
  const POR_SECCION = 8, POR_PAGINA = 24;
  const campo = (id, etiqueta, el) => h('div', {}, h('label', { for: id }, etiqueta), el);
  const q = h('input', { id: 'q', type: 'search', placeholder: 'Calculadora, libro de cálculo…', maxlength: 100 });
  const cat = h('select', { id: 'cat' }, h('option', { value: '' }, 'Todas'));
  const min = h('input', { id: 'min', type: 'number', inputmode: 'decimal', min: 0, step: 'any', placeholder: '0', 'aria-describedby': 'error-filtros' });
  const max = h('input', { id: 'max', type: 'number', inputmode: 'decimal', min: 0, step: 'any', placeholder: 'Sin tope', 'aria-describedby': 'error-filtros' });
  const est = h('select', { id: 'est' }, h('option', { value: '' }, 'Cualquiera'),
    h('option', { value: 'NUEVO' }, 'Nuevo'), h('option', { value: 'USADO' }, 'Usado'));
  const errorFiltros = h('p', { class: 'error', id: 'error-filtros', role: 'alert', hidden: true });
  const resultados = h('div', { 'aria-busy': 'false' });
  const info = h('p', { class: 'sub', role: 'status', 'aria-live': 'polite' });
  let cats = [];

  function limpiarFiltros() {
    q.value = ''; cat.value = ''; min.value = ''; max.value = ''; est.value = '';
    errorFiltros.hidden = true;
    buscar();
  }
  const limpiar = h('button', { class: 'btn sec', type: 'button', onclick: limpiarFiltros }, 'Quitar filtros');
  const limpiarCaja = h('div', { class: 'acciones fin', hidden: true }, limpiar);
  [min, max].forEach((el) => el.addEventListener('input', () => { errorFiltros.hidden = true; }));

  function pintarPlano(lista) {
    let limite = POR_PAGINA;
    const rejilla = h('div', { class: 'rejilla' });
    const mas = h('button', { class: 'btn sec', type: 'button' }, 'Mostrar más');
    const dibujar = () => { rejilla.replaceChildren(...lista.slice(0, limite).map(tarjeta)); mas.hidden = limite >= lista.length; };
    mas.addEventListener('click', () => { limite += POR_PAGINA; dibujar(); });
    dibujar();
    resultados.replaceChildren(rejilla, h('div', { class: 'acciones centradas' }, mas));
  }
  function pintarSecciones(lista) {
    const porCat = new Map();
    for (const p of lista) { if (!porCat.has(p.categoria)) porCat.set(p.categoria, []); porCat.get(p.categoria).push(p); }
    const orden = cats.map((c) => c.nombre).filter((n) => porCat.has(n));
    for (const n of porCat.keys()) if (!orden.includes(n)) orden.push(n);
    resultados.replaceChildren(...orden.map((n) => {
      const items = porCat.get(n);
      return h('section', { class: 'seccion' },
        h('div', { class: 'seccion-cab' }, h('h2', {}, n, h('span', { class: 'meta' }, ' · ' + items.length)),
          items.length > POR_SECCION ? h('button', { class: 'btn sec', type: 'button', onclick: () => { cat.value = n; buscar(); } }, 'Ver todos (' + items.length + ')') : null),
        h('div', { class: 'rejilla' }, items.slice(0, POR_SECCION).map(tarjeta)));
    }));
  }
  function panelError(mensaje) {
    return h('div', { class: 'panel vacio' },
      h('p', {}, 'No se pudieron cargar las publicaciones.'),
      h('p', { class: 'meta' }, mensaje),
      h('div', { class: 'acciones' },
        h('button', { class: 'btn', type: 'button', onclick: buscar }, 'Reintentar')));
  }
  function panelVacio(hayFiltros) {
    return h('div', { class: 'panel vacio' },
      h('p', {}, hayFiltros ? 'No encontramos publicaciones con esos filtros.' : 'Todavía no hay publicaciones. ¡Sé el primero en publicar!'),
      h('div', { class: 'acciones' },
        hayFiltros ? h('button', { class: 'btn sec', type: 'button', onclick: limpiarFiltros }, 'Quitar filtros') : null,
        h('a', { class: 'btn', href: '#/publicar' }, 'Publicar un artículo')));
  }

  async function buscar() {
    errorFiltros.hidden = true;
    const minV = min.value === '' ? null : Number(min.value);
    const maxV = max.value === '' ? null : Number(max.value);
    if (minV !== null && maxV !== null && minV > maxV) {
      errorFiltros.textContent = 'El precio mínimo no puede ser mayor que el máximo.';
      errorFiltros.hidden = false;
      min.focus();
      return;
    }
    const ps = new URLSearchParams();
    if (q.value.trim()) ps.set('texto', q.value.trim());
    if (cat.value) ps.set('categoria', cat.value);
    if (min.value) ps.set('precioMin', min.value);
    if (max.value) ps.set('precioMax', max.value);
    if (est.value) ps.set('estado', est.value);
    const hayFiltros = ps.toString() !== '';
    limpiarCaja.hidden = !hayFiltros;
    resultados.setAttribute('aria-busy', 'true');
    resultados.replaceChildren(esqueletoCatalogo());
    info.textContent = 'Cargando publicaciones…';
    try {
      const lista = await api('GET', '/api/publicaciones' + (hayFiltros ? '?' + ps : ''));
      limpiarCaja.hidden = !hayFiltros || lista.length === 0;
      info.textContent = hayFiltros
        ? (lista.length === 1 ? '1 resultado' : lista.length + ' resultados')
        : (lista.length === 1 ? '1 publicación disponible' : lista.length + ' publicaciones disponibles');
      if (!lista.length) resultados.replaceChildren(panelVacio(hayFiltros));
      else if (hayFiltros) pintarPlano(lista);
      else pintarSecciones(lista);
    } catch (e) {
      info.textContent = '';
      resultados.replaceChildren(panelError(e.message));
      aviso(e.message, true);
    } finally {
      resultados.setAttribute('aria-busy', 'false');
    }
  }

  const filtros = h('form', { class: 'tarjeta filtros', onsubmit: (ev) => { ev.preventDefault(); buscar(); } },
    h('div', { class: 'buscar' }, h('label', { for: 'q' }, 'Buscar'), q),
    campo('cat', 'Categoría', cat), campo('min', 'Precio mínimo', min), campo('max', 'Precio máximo', max),
    campo('est', 'Estado', est),
    h('button', { class: 'btn', type: 'submit' }, 'Buscar'),
    errorFiltros);
  montar(h('h1', {}, 'Catálogo'), h('p', { class: 'sub' }, 'Lo que tus compañeros están vendiendo, ordenado y en un solo lugar.'),
    filtros, limpiarCaja, info, resultados);

  try {
    cats = await api('GET', '/api/categorias');
    cat.append(...cats.map((c) => h('option', { value: c.nombre }, `${c.nombre} (${c.disponibles})`)));
  } catch (e) {
    aviso('No se pudieron cargar las categorías: ' + e.message, true);
  }
  await buscar();
}

/* ---------- detalle y contacto (HU-11) ---------- */
async function vistaDetalle(id) {
  const p = await api('GET', '/api/publicaciones/' + id);
  document.title = p.titulo + ' · UPMarket';
  const s = leerSesion();
  const esMia = s && s.usuario === p.vendedor;
  const disponible = p.estadoPublicacion === 'DISPONIBLE';
  const total = p.fotos.length;

  const vend = { nombre: nombreCorto(p.vendedor), fotoUrl: null };
  const avatarVendedor = avatar(null, vend.nombre, 'mediano');
  const nombreVendedor = h('strong', {}, vend.nombre);
  api('GET', '/api/perfil/publico?usuario=' + encodeURIComponent(p.vendedor))
    .then((perfil) => {
      if (!perfil) return;
      if (perfil.nombre) { vend.nombre = perfil.nombre; nombreVendedor.textContent = perfil.nombre; }
      avatarVendedor.replaceWith(avatar(perfil.fotoUrl, vend.nombre, 'mediano'));
    })
    .catch(() => { /* se mantiene el nombre corto */ });

  let actual = 0;
  const grande = h('img', { class: 'grande', decoding: 'async' });
  const contador = h('span', { class: 'contador', role: 'status' }, '');
  const minis = p.fotos.map((f, i) => h('button', {
    type: 'button', class: 'mini', 'aria-pressed': String(i === 0),
    'aria-label': 'Ver foto ' + (i + 1) + ' de ' + total,
    onclick: () => mostrarFoto(i),
  }, h('img', { src: API + f.url, alt: '', loading: 'lazy', decoding: 'async' })));

  function mostrarFoto(i) {
    actual = i;
    grande.src = API + p.fotos[i].url;
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
  const btnContacto = disponible ? h('button', { class: 'btn', type: 'button', onclick: async () => {
    btnContacto.disabled = true;
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
    } catch (e) { btnContacto.disabled = false; aviso(e.message, true); }
  } }, 'Ver WhatsApp o correo del vendedor') : null;

  const estadoVendido = disponible ? null : h('div', { class: 'estado-vendido', role: 'status' },
    h('strong', {}, 'Esta publicación ya se vendió'),
    h('p', {}, 'El vendedor ya no recibe consultas por este artículo.'));

  const acciones = esMia ? h('div', { class: 'acciones' },
    disponible ? h('a', { class: 'btn', href: '#/editar/' + id }, 'Editar') : null,
    disponible ? h('button', { class: 'btn sec', onclick: () => accion(() => api('POST', `/api/publicaciones/${id}/sold`), 'Marcada como vendida', () => vistaDetalle(id)) }, 'Marcar como vendido') : null,
    h('button', { class: 'btn peligro', onclick: () => { if (confirm('¿Eliminar esta publicación? No se puede deshacer.')) accion(() => api('DELETE', '/api/publicaciones/' + id), 'Publicación eliminada', () => { location.hash = '#/mias'; }); } }, 'Eliminar')) : null;

  montar(
    h('a', { class: 'volver', href: '#/' }, '← Volver al catálogo'),
    h('header', { class: 'detalle-cab' },
      h('div', { class: 'detalle-cab__texto' },
        h('h1', { class: 'detalle-titulo', tabindex: '-1' }, p.titulo),
        h('div', { class: 'etiquetas' },
          h('span', { class: 'etiqueta' + (p.estadoArticulo === 'USADO' ? ' usado' : '') }, p.estadoArticulo === 'USADO' ? 'Usado' : 'Nuevo'),
          h('span', { class: 'etiqueta cat' }, p.categoria),
          disponible ? null : h('span', { class: 'etiqueta vendida' }, 'Vendido'))),
      h('div', { class: 'detalle-precio' + (disponible ? '' : ' vendido') }, precio(p.precio))),
    h('div', { class: 'detalle' },
      galeria,
      h('div', { class: 'panel' },
        h('h2', { class: 'detalle-sub' }, 'Descripción'),
        h('p', { class: 'detalle-desc' }, p.descripcion),
        h('h2', { class: 'detalle-sub' }, 'Vendedor'),
        h('div', { class: 'vendedor' }, avatarVendedor,
          h('div', {}, nombreVendedor, h('div', { class: 'meta' }, 'Publicado el ' + fecha(p.publicadaEn)))),
        btnContacto, estadoVendido, zonaContacto, acciones)));
}
async function accion(fn, exito, despues) {
  try { await fn(); aviso(exito); await despues(); } catch (e) { aviso(e.message, true); }
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
  async function subirArchivos(lista) {
    for (const a of Array.from(lista)) {
      if (fotos.length >= 5) { aviso('Máximo 5 fotos por publicación', true); break; }
      if (!/^image\/(jpeg|png)$/.test(a.type)) { aviso(a.name + ': usa JPG o PNG', true); continue; }
      if (a.size > 5 * 1024 * 1024) { aviso(a.name + ' pesa más de 5 MB', true); continue; }
      const fd = new FormData(); fd.append('archivo', a);
      try { fotos.push(await api('POST', '/api/fotos', fd, true)); pintarPrevias(); } catch (e) { aviso(e.message, true); }
    }
  }
  function pintarPrevias() {
    previas.replaceChildren(...fotos.map((f, i) => h('figure', {}, h('img', { src: API + f.url, alt: 'Foto ' + (i + 1) }),
      h('button', { type: 'button', 'aria-label': 'Quitar foto ' + (i + 1), onclick: () => { fotos.splice(i, 1); pintarPrevias(); } }, iconoQuitar()))));
    if (cuenta) {
      cuenta.replaceChildren(h('strong', {}, fotos.length + ' de 5'), fotos.length === 1 ? ' · foto agregada' : ' · fotos agregadas');
      const llena = fotos.length >= 5;
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
      ? h('img', { src: API + foto.url, alt: t ? 'Portada de ' + t : 'Foto de portada', loading: 'lazy' })
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
    boton.disabled = true; boton.setAttribute('aria-busy', 'true'); boton.textContent = actual ? 'Guardando…' : 'Publicando…';
    try {
      if (actual) { await api('PUT', '/api/publicaciones/' + id, base); aviso('Cambios guardados'); location.hash = '#/p/' + id; }
      else {
        const nueva = await api('POST', '/api/publicaciones', { ...base, fotos,
          whatsapp: usarTel.checked ? telParaApi(tel.value) : null, correo: usarMail.checked ? mail.value.trim() : null });
        aviso('¡Publicación creada!'); location.hash = '#/p/' + nueva.id;
      }
    } catch (e) { mostrar(e.message); }
    finally { boton.disabled = false; boton.removeAttribute('aria-busy'); boton.textContent = etiquetaBoton; }
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
    ? h('img', { class: 'mias-fila__foto', src: API + p.fotos[0].url, alt: '', loading: 'lazy' })
    : h('div', { class: 'mias-fila__foto mias-fila__foto--vacia' }, 'Sin foto');
  const cuando = vendida && p.vendidaEn
    ? 'Vendida el ' + fecha(p.vendidaEn)
    : 'Publicado el ' + fecha(p.publicadaEn);
  return h('li', { class: 'mias-fila' + (vendida ? ' mias-fila--vendida' : '') },
    h('a', { class: 'mias-fila__principal', href: '#/p/' + p.id },
      foto,
      h('div', { class: 'mias-fila__texto' },
        h('span', { class: 'mias-fila__titulo' }, p.titulo),
        h('span', { class: 'mias-fila__meta' },
          [p.categoria, p.estadoArticulo === 'USADO' ? 'Usado' : 'Nuevo', cuando].join(' · ')))),
    h('div', { class: 'mias-fila__cifras' },
      h('span', { class: 'mias-fila__precio' }, precio(p.precio)),
      h('span', { class: 'etiqueta' + (vendida ? ' vendida' : '') }, vendida ? 'Vendida' : 'Disponible')),
    h('div', { class: 'mias-fila__acciones' },
      h('button', { class: 'btn-mini', type: 'button', 'aria-label': 'Editar ' + p.titulo, onclick: () => { location.hash = '#/editar/' + p.id; } }, 'Editar'),
      vendida ? null : h('button', { class: 'btn-mini', type: 'button', 'aria-label': 'Marcar como vendida ' + p.titulo,
        onclick: (ev) => { const boton = ev.currentTarget; boton.disabled = true;
          accion(() => api('POST', '/api/publicaciones/' + p.id + '/sold'), 'Publicación marcada como vendida', () => vistaMias(true))
            .finally(() => { if (boton.isConnected) boton.disabled = false; }); } }, 'Marcar vendida'),
      h('button', { class: 'btn-mini btn-mini--peligro', type: 'button', 'aria-label': 'Eliminar ' + p.titulo,
        onclick: () => { if (confirm('¿Eliminar «' + p.titulo + '»? Esta acción no se puede deshacer.')) accion(() => api('DELETE', '/api/publicaciones/' + p.id), 'Publicación eliminada', () => vistaMias(true)); } }, 'Eliminar')));
}

function esqueletoMias() {
  return h('ul', { class: 'mias-esqueleto', 'aria-hidden': 'true' },
    Array.from({ length: 3 }, () => h('li', {},
      h('span', { class: 'bloque bloque--foto' }),
      h('span', {}, h('span', { class: 'bloque bloque--titulo' }), h('span', { class: 'bloque bloque--meta' })),
      h('span', { class: 'bloque bloque--precio' }))));
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
async function vistaPerfil() {
  const s = leerSesion();
  let p;
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

  const foto = h('div');
  const previaFoto = h('div');
  const previaNombre = h('span', { class: 'nombre-previa' }, p.nombre);
  const previaTel = h('span', { class: 'meta' });
  const nombreVisible = () => nombre.value.trim() || p.nombre;
  const pintarFoto = () => {
    foto.replaceChildren(avatar(fotoUrl, nombreVisible(), 'grande', 'Foto de perfil de ' + nombreVisible()));
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
    if (a.size > 5 * 1024 * 1024) { aviso('La foto pesa más de 5 MB', true); return; }
    const fd = new FormData(); fd.append('archivo', a);
    try { const r = await api('POST', '/api/fotos', fd, true); fotoUrl = r.url; pintarFoto(); aviso('Foto lista: pulsa Guardar cambios'); }
    catch (e) { aviso(e.message, true); }
  } });

  const confirmacion = h('input', { id: 'conf', autocomplete: 'off', placeholder: 'ELIMINAR', 'aria-describedby': 'ayuda-conf' });
  const borrar = h('button', { class: 'btn peligro', type: 'button', disabled: true, onclick: async () => {
    borrar.disabled = true; borrar.textContent = 'Eliminando…';
    try {
      await api('DELETE', '/api/perfil');
      localStorage.removeItem(CLAVE_TOKEN); perfilActual = null; location.hash = '#/login';
      aviso('Tu cuenta fue eliminada');
    } catch (e) {
      aviso(e.message, true); borrar.disabled = false; borrar.textContent = 'Eliminar mi cuenta definitivamente';
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
          guardar.disabled = true; guardar.setAttribute('aria-busy', 'true'); guardar.textContent = 'Guardando…';
          try {
            perfilActual = await api('PUT', '/api/perfil', { nombre: nombre.value.trim(), telefono: telParaApi(tel.value), fotoUrl });
            fotoUrl = perfilActual.fotoUrl; error.hidden = true; pintarFoto(); pintarNav(); aviso('Perfil actualizado');
          } catch (e) { mostrarError(e.message); }
          finally { guardar.disabled = false; guardar.removeAttribute('aria-busy'); guardar.textContent = 'Guardar cambios'; }
        } },
          h('div', { class: 'perfil-cab' }, foto,
            h('div', { class: 'acciones', style: 'margin:0' },
              h('button', { class: 'btn sec', type: 'button', onclick: () => subir.click() }, 'Cambiar foto'),
              h('button', { class: 'btn sec', type: 'button', onclick: () => { fotoUrl = null; pintarFoto(); } }, 'Quitar foto'), subir)),
          h('p', { class: 'ayuda' }, 'JPG o PNG, hasta 5 MB. La foto se aplica al guardar.'),
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

/* ---------- categorías (HU-9, solo ADMIN) ---------- */
async function vistaCategorias() {
  if (!leerSesion().esAdmin) throw new Error('Solo un administrador puede gestionar categorías');

  const numero = new Intl.NumberFormat('es-CO');
  const nombre = h('input', {
    id: 'categoria-nombre', name: 'nombre', required: true, minlength: 2, maxlength: 40,
    autocomplete: 'off', placeholder: 'Ej. Deportes',
    'aria-describedby': 'categoria-ayuda categoria-error',
  });
  const ayuda = h('p', { id: 'categoria-ayuda', class: 'ayuda' }, 'Entre 2 y 40 caracteres. No puede repetirse.');
  const error = h('p', { id: 'categoria-error', class: 'error', role: 'alert', hidden: true });
  const crear = h('button', { class: 'btn', type: 'submit' }, 'Crear categoría');
  const resumen = h('p', { class: 'resumen' });
  const cuerpo = h('tbody');
  const lista = h('div', { class: 'tabla-wrap', role: 'region', 'aria-label': 'Tabla de categorías', tabindex: '0' },
    h('table', { class: 'tabla-cats' },
      h('caption', { class: 'sr-only' }, 'Categorías del catálogo y número de publicaciones disponibles'),
      h('thead', {}, h('tr', {},
        h('th', { scope: 'col' }, 'Categoría'),
        h('th', { scope: 'col', class: 'num' }, 'Publicaciones disponibles'))),
      cuerpo));
  let creando = false;
  let cargaId = 0;

  nombre.addEventListener('input', () => {
    error.hidden = true;
    nombre.removeAttribute('aria-invalid');
  });

  function pintar(cats) {
    const total = cats.reduce((t, c) => t + (Number(c.disponibles) || 0), 0);
    resumen.textContent = `${numero.format(cats.length)} ${cats.length === 1 ? 'categoría' : 'categorías'} · ${numero.format(total)} ${total === 1 ? 'publicación disponible' : 'publicaciones disponibles'}`;
    if (cats.length === 0) {
      cuerpo.replaceChildren(h('tr', {}, h('td', { colspan: '2' },
        h('div', { class: 'vacio' },
          h('p', {}, 'Todavía no hay categorías.'),
          h('p', { class: 'ayuda' }, 'Crea la primera con el formulario de la izquierda.')))));
      return;
    }
    cuerpo.replaceChildren(...cats.map((c) => h('tr', {},
      h('th', { scope: 'row' }, c.nombre),
      h('td', { class: 'num' }, c.disponibles === null || c.disponibles === undefined ? '—' : numero.format(c.disponibles)))));
  }

  async function cargar(silencioso = false) {
    const id = ++cargaId;
    lista.setAttribute('aria-busy', 'true');
    if (!silencioso) {
      resumen.textContent = '';
      cuerpo.replaceChildren(...[0, 1, 2].map(() => h('tr', { class: 'cargando' },
        h('td', {}, h('span', { class: 'barra-carga' })),
        h('td', { class: 'num' }, h('span', { class: 'barra-carga barra-corta' })))));
    }
    try {
      const cats = await api('GET', '/api/categorias');
      if (id !== cargaId) return;
      pintar(cats);
    } catch (e) {
      if (id !== cargaId) return;
      resumen.textContent = 'No se pudieron cargar';
      cuerpo.replaceChildren(h('tr', {}, h('td', { colspan: '2' },
        h('div', { class: 'vacio' },
          h('p', {}, e.message),
          h('button', { class: 'btn sec', type: 'button', onclick: () => cargar() }, 'Reintentar')))));
    } finally {
      if (id === cargaId) lista.removeAttribute('aria-busy');
    }
  }

  const formulario = h('form', { class: 'panel', novalidate: true, onsubmit: async (ev) => {
    ev.preventDefault();
    if (creando) return;
    error.hidden = true;
    nombre.removeAttribute('aria-invalid');
    const valor = nombre.value.trim().replace(/\s+/g, ' ');
    if (valor.length < 2) {
      error.textContent = 'Escribe un nombre de al menos 2 caracteres.';
      error.hidden = false;
      nombre.setAttribute('aria-invalid', 'true');
      nombre.focus();
      return;
    }
    creando = true;
    crear.disabled = true;
    crear.textContent = 'Creando…';
    try {
      await api('POST', '/api/categorias', { nombre: valor });
      nombre.value = '';
      nombre.focus();
      aviso('Categoría creada');
      await cargar(true);
    } catch (e) {
      error.textContent = e.message;
      error.hidden = false;
      nombre.setAttribute('aria-invalid', 'true');
      nombre.focus();
    } finally {
      creando = false;
      crear.disabled = false;
      crear.textContent = 'Crear categoría';
    }
  } },
    h('h2', { class: 'panel-titulo' }, 'Nueva categoría'),
    h('label', { for: 'categoria-nombre' }, 'Nombre'),
    nombre, ayuda, error,
    h('div', { class: 'acciones' }, crear));

  montar(
    h('h1', {}, 'Categorías'),
    h('p', { class: 'sub' }, 'Administra las categorías del catálogo. Solo administradores.'),
    h('div', { class: 'categorias' }, formulario,
      h('section', { class: 'panel', 'aria-labelledby': 'cats-titulo' },
        h('div', { class: 'seccion-cab' },
          h('h2', { id: 'cats-titulo' }, 'Categorías existentes'),
          resumen),
        lista)));
  await cargar();
}
