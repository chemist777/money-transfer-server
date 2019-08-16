# Money transfer server

It's standalone web server to provide money transfer REST API.

It stores all the data in memory and doesn't support persistence and crash recovery (no WAL journal).

But it supports various cool features:

- Non-blocking network IO using reactor-netty.
- Lock-free money transfers using CAS (compare and swap).
- Idempotent REST API using `Idempotention-Key` header, so client shouldn't worry about HTTP request retries.

However this server should be improved a lot to be production ready.

## Requirements

Java 11 to run and Maven to build.

## Running

`java -jar money-transfer-server.jar`

## Server configuration

| Name | Data type | Description | Default value |
| ---- | --------- | ----------- | ------------- |
| `host` | String | Listening host. | 0.0.0.0
| `port` | int | Listening port. | 4646
| `backlog` | int | Maximum length of accept socket queue in kernel. | 10240
| `nioThreads` | int | Number of NIO threads. | cpu_cores / 2
| `processingThreads` | int | Number of processing threads. | cpu_cores / 2
| `balanceMaxScale` | int | Maximum supported number of digits after decimal point. | 2
| `idempotencyKeyCacheLifetimeSec` | long | Expiration time for idempotency key in seconds. | 86400 (1 day)

## API methods
### Transfer money
`POST /transfer`

You must provide random `Idempotency-Key` header in every request.

#### GET parameters

All parameters are required.

| Name | Data type | Description | Example |
| ---- | --------- | ----------- | ------- |
| `sender` | String | Source account id. | a |
| `recipient` | String | Destination account id. | b |
| `amount` | BigDecimal | Amount in USD with point as decimal separator. | 5.25 |

#### Response

In case of successful transfer it returns `200 OK` HTTP code.

##### HTTP codes

| Code | Description |
| ---- | ----------- |
| 200 | Successful transaction. |
| 400 | Invalid request parameters. More info can be found in the response body. |
| 500 | Internal server error or insufficient account balance. More info can be found in the response body. |

In production system would be better to return JSON in response body like this:
```json
{
  "error_code": "not_enough_money",
  "error_message": "Account 'a' doesn't have enough money."
}
```
