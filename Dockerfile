FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json tsconfig.json vitest.config.ts ./
COPY packages ./packages
COPY docs ./docs
COPY README.md ./README.md
COPY ROADMAP.md ./ROADMAP.md
COPY CONTRIBUTING.md ./CONTRIBUTING.md
COPY LICENSE ./LICENSE
COPY QUICKSTART.md ./QUICKSTART.md

RUN npm ci
RUN npm run build

FROM node:20-alpine AS runtime
WORKDIR /app
RUN npm install -g serve
COPY --from=build /app/packages/web/dist ./dist
EXPOSE 3000
CMD ["serve", "-s", "dist", "-l", "3000"]
