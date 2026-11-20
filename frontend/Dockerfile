# Frontend estático + proxy inverso. Es la ÚNICA puerta pública: el navegador ve un solo origen,
# por eso no hace falta CORS ni tocar AuthCore.
# El panel de administración (React + Vite) se compila aquí y se sirve bajo /admin/.

FROM node:20-alpine AS admin
WORKDIR /admin
COPY frontend-admin/package.json frontend-admin/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend-admin/ ./
RUN npm run build

FROM nginx:1.27-alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY frontend/nginx.conf /etc/nginx/conf.d/upmarket.conf
COPY frontend/index.html /usr/share/nginx/html/index.html
COPY frontend/css /usr/share/nginx/html/css
COPY frontend/js /usr/share/nginx/html/js
COPY frontend/public /usr/share/nginx/html/public
COPY --from=admin /admin/dist /usr/share/nginx/html/admin
EXPOSE 80
