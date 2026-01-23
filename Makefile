postgres-up:
	docker run --name shipmonk-postgres \
	  -e POSTGRES_USER=shipmonk \
	  -e POSTGRES_PASSWORD=secret \
	  -e POSTGRES_DB=shipmonk-exchange-rate-db \
	  -p 5432:5432 \
	  -v shipmonk_pgdata:/var/lib/postgresql \
	  -d postgres:18

postgres-down:
	docker stop shipmonk-postgres || true
	docker rm shipmonk-postgres || true

postgres-ps:
	docker ps -a | grep shipmonk-postgres || true

postgres-clean:
	docker volume rm shipmonk_pgdata || true

schema-up:
	psql -h localhost -U shipmonk -d shipmonk-exchange-rate-db -f migrations/0001_init_schema.up.sql || \
	docker exec -i shipmonk-postgres psql -U shipmonk -d shipmonk-exchange-rate-db < migrations/0001_init_schema.up.sql

schema-down:
	psql -h localhost -U shipmonk -d shipmonk-exchange-rate-db -f migrations/0001_init_schema.down.sql || \
	docker exec -i shipmonk-postgres psql -U shipmonk -d shipmonk-exchange-rate-db < migrations/0001_init_schema.down.sql


build:
	mvn clean package

run:
	java -jar target/testingday-exchange-rates-0.0.1-SNAPSHOT.jar
