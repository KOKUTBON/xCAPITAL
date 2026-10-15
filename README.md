# xCAPITAL

A treasury workspace for tracking company cash, forecasting balances, and reviewing financial approvals.

[Open the demo](https://kokutbon.github.io/xCAPITAL/) · [Technical documentation](docs/README.md)

## Features

- See cash positions across accounts and companies.
- Plan cash flow with a 13-week forecast.
- Review approvals, ledger entries, and risk scenarios.

## Screenshots

![xCAPITAL main workspace](docs/screenshots/command-center.png)

![xCAPITAL detail view](docs/screenshots/forecast.png)

## Technology

Angular, Flutter, Spring Boot, FastAPI, PostgreSQL, Redis, Docker.

## Run the demo locally

```sh
git clone https://github.com/KOKUTBON/xCAPITAL.git
cd xCAPITAL
cd frontend/web
npm ci
npm start
```

Open http://localhost:4200/. The browser demo uses sample data. See the technical documentation for the full application setup.

## License

[Apache License 2.0](LICENSE).
