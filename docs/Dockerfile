FROM node:22-alpine as builder

COPY systemofadownload.yaml ./
RUN npm install -g redoc-cli && redoc-cli bundle -o index.html systemofadownload.yaml

FROM nginx as webserver

COPY --from=builder index.html /usr/share/nginx/html/
