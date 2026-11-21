/* Aplica el tema guardado antes del primer pintado. Sin preferencia explícita, el CSS sigue
 * a prefers-color-scheme. Debe ser un archivo externo: la CSP no permite scripts en línea. */
(function () {
  try {
    var tema = localStorage.getItem('upmarket.tema');
    if (tema === 'claro' || tema === 'oscuro') {
      document.documentElement.setAttribute('data-tema', tema);
    }
  } catch (e) { /* sin almacenamiento: se sigue la preferencia del sistema */ }
})();
