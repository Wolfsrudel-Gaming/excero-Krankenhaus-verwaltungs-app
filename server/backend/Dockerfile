FROM node:20-alpine
RUN apk add --no-cache postgresql16-client
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev
COPY src ./src
COPY public ./public
COPY public-excero ./public-excero
EXPOSE 8090
CMD ["node", "src/index.js"]
