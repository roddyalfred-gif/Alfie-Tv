# Deployment Guide

This document summarizes the deployment and operations workflow for Alfie TV.

## Overview

The repository includes:
- Docker-based container builds
- GitHub Actions CI validation
- a production-oriented web build path for the web package
- shared backend/core packages intended for deployment behind a standard web host or container runtime

## Prerequisites

- Node.js 20+
- npm 10+
- Docker (optional, for containerized deployment)

## Local Build Verification

```bash
npm install
npm test
npm run build
```

## Docker Build

Build the image from the repository root:

```bash
docker build -t alfie-tv .
```

Run the container locally:

```bash
docker run -p 3000:3000 alfie-tv
```

## CI/CD

The repository includes a GitHub Actions workflow for automated validation on pushes and pull requests. The workflow runs install, test, and build steps to keep the monorepo healthy.

## Production Checklist

- Verify environment variables and secrets are configured
- Confirm the backend and web packages are built successfully
- Review logs and health endpoints before rollout
- Apply secure hosting defaults such as HTTPS, CORS restrictions, and least-privilege credentials

## Security Notes

- Keep dependencies up to date
- Rotate secrets regularly
- Review authentication and persistence paths before exposing the app publicly

## Performance and Load Notes

- Measure bundle size and startup time for the web app
- Test the backend under realistic traffic before full rollout
- Monitor CPU, memory, and storage usage in production
