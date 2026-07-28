# 1. asama: React uygulamasini derle. Bu asamanin ciktisi sadece /app/build klasoru;
# node_modules ve kaynak kodu son imaja hic girmiyor (multi-stage build).
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
# "npm ci" ("npm install" degil): package-lock.json'i birebir uygular, yani imaj her
# kurulusta ayni bagimlilik surumleriyle cikar. "npm install" lock dosyasini gunceleyebilir.
RUN npm ci
COPY . .
RUN npm run build

# 2. asama: derlenmis statik dosyalari nginx ile servis et. "npm start"tan farki: orada
# webpack dev server calisiyor (hot reload, derlenmemis kod); burada onceden derlenmis,
# kucultulmus statik dosyalar var — kullaniciya gidecek olan bu.
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
EXPOSE 80
