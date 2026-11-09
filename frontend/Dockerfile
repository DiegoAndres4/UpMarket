# Frontend estático + proxy inverso. Es la ÚNICA puerta pública: el navegador ve un solo origen,
# por eso no hace falta CORS ni tocar AuthCore.
FROM nginx:1.27-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/upmarket.conf
COPY index.html /usr/share/nginx/html/index.html
COPY css /usr/share/nginx/html/css
COPY js /usr/share/nginx/html/js
EXPOSE 80
