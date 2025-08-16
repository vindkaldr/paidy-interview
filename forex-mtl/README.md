# Running locally
1) Build the Forex API image
```shell
docker build . --tag forex:1.0.1
```
2) Bring up all services
```shell
docker-compose up
```
3) Invoke the Forex API
```shell
curl "http://localhost:8090/rates?from=JPY&to=USD"
```