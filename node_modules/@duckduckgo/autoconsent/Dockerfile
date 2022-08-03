FROM node:12

RUN apt update && apt install -y brotli jq
RUN mkdir -p /app/ && chown -R node:node /app
USER node
COPY package.json /app/
COPY package-lock.json /app/
WORKDIR /app/
RUN npm ci
