## Forex API
A local proxy for getting exchange rates.
## Supported currencies
AUD, CAD, CHF, EUR, GBP, NZD, JPY, SGD, USD
## API
### GET /rates?from=USD&to=JPY
- Rate is available and not older than 5 minutes:
  ```
  HTTP status code: 200
  {
    "from": "USD",
    "to": "JPY",
    "price": 0.439097543438065,
    "timestamp": "2025-08-25T15:43:26.386Z"
  }
  ```
- Rate is not available or older than 5 minutes:
  ```
  HTTP status code: 404
  Rate not found: USD to JPY
  ```
- Currency cannot be retrieved due to unexpected (infrastructure etc.) issue:
  ```
  HTTP status code: 503
  Unexpected error occurred
  ```
- Currency cannot be retrieved due to invalid requests:
  ```
  HTTP status code: 400
  Missing query parameter: 'from'
  Missing query parameter: 'to'
  Invalid 'from' currency: HUF
  Invalid 'to' currency: HUF
  ```
## Implementation
- Behind the scenes we rely on a **quota limited (1000 requests/day) OneFrame API**
- The OneFrame API is queried for all supported exchange rates and **cached in Redis with 5 minutes TTL**
- A background job **rebuilds the cache every 4 minutes** before the TTL passes
  - **On failure**, we will attempt **rebuilding the cache every 10 seconds until success**
- If there is no issue, it means **360 requests/day made to the OneFrame API**
  - In case of issue, we have **on average 1.7 rebuild attempt left from the quota** on failure

## Testing
- The tests are not mirroring the structure of the code (there is no 1 test file per 1 production file) to avoid fragility
- More elaborate cases still could be added to cover:
  - The timing of the cache rebuilding and
  - Different variation when our dependencies (OneFrame and Redis) are temporarily not available etc. 

## Note from the author
- The background job for rebuilding the cache is now part of the application itself. In case of horizontal scaling, to avoid draining the OneFrame quota, it is advised to split the background job into a separate process

## Assumptions
- The 5 minutes rule is a must and users cannot make use of too old exchange rates
- The same currency can be provided for the from and to parameters. In this case a rate is returned with the price of 1

## Simplifications
- Apart from the code, in terms of the infrastructure, at minimum I'd additionally set up:
  - A load balancer because horizontal scaling and rate limiting
  - Scale Redis horizontally based on expected usage
  - Log collection
  - Metrics collection (for example: hardware resource usage, response time percentiles, response/second traffic, returned status codes, number of time we failed to reach OneFrame or Redis)
  - Alerting based on previously collected log and/or metrics
  - Use an automation tool (like Jenkins) for keep the credentials out of the source code. Or maybe something like Hashicorp Vault
    - Automated testing (including performance testing, for example with Gatling), vulnerability check and the release process itself

## How to run the tests
```
docker-compose up
sbt clean test
```
## How to run the application
```
docker-compose up
sbt run
curl "http://localhost:8080/rates?from=USD&to=JPY"
```