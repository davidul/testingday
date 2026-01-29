# ========================================
# Exchange Rates Application - Makefile
# ========================================
#
# Database Management:
#   make postgres-up        - Start PostgreSQL in Docker
#   make postgres-down      - Stop and remove PostgreSQL container
#   make postgres-ps        - Check PostgreSQL container status
#   make postgres-clean     - Remove PostgreSQL data volume
#
# Application Build & Run:
#   make build              - Build the application JAR
#   make build-skip-tests   - Build the application JAR skipping tests
#   make run                - Run with default profile (dev)
#   make run-dev            - Run JAR with DEV profile
#   make run-prod           - Run JAR with PROD profile
#   make dev                - Run with Maven in DEV profile (hot reload)
#   make prod               - Run with Maven in PROD profile
#   make build-run-dev      - Build and run in DEV profile
#   make build-run-prod     - Build and run in PROD profile
#
# Testing & Reports:
#   make test               - Run unit tests only
#   make test-all           - Run unit and integration tests
#   make test-report        - Generate test reports with coverage
#   make view-report        - Open test reports in browser
#
# Docker Commands:
#   make docker-build       - Build Docker image
#   make docker-run         - Run application in Docker container
#   make docker-stop        - Stop Docker container
#   make docker-logs        - View Docker container logs
#   make docker-up          - Start all services with docker-compose
#   make docker-down        - Stop all services with docker-compose
#   make docker-restart     - Restart all services
#   make docker-clean       - Remove all containers and images
#
# ========================================


.PHONY: help postgres-up postgres-down postgres-ps postgres-clean schema-up schema-down build run run-dev run-prod dev prod build-run-dev build-run-prod test test-all test-report view-report docker-build docker-run docker-stop docker-logs docker-up docker-down docker-restart docker-clean

# Default target - show help when running 'make' without arguments
.DEFAULT_GOAL := help

help:
	@echo "=========================================="
	@echo "Exchange Rates Application - Makefile"
	@echo "=========================================="
	@echo ""
	@echo "Database Management:"
	@echo "  make postgres-up      - Start PostgreSQL in Docker"
	@echo "  make postgres-down    - Stop and remove PostgreSQL container"
	@echo "  make postgres-ps      - Check PostgreSQL container status"
	@echo "  make postgres-clean   - Remove PostgreSQL data volume (WARNING: deletes all data)"
	@echo ""
	@echo "Application Build & Run:"
	@echo "  make build            - Build the application JAR"
	@echo "  make build-skip-tests - Build the application JAR skipping tests"
	@echo "  make run              - Run with default profile (dev)"
	@echo "  make run-dev          - Run JAR with DEV profile"
	@echo "  make run-prod         - Run JAR with PROD profile"
	@echo "  make dev              - Run with Maven in DEV profile (hot reload)"
	@echo "  make prod             - Run with Maven in PROD profile"
	@echo "  make build-run-dev    - Build and run in DEV profile"
	@echo "  make build-run-prod   - Build and run in PROD profile"
	@echo ""
	@echo "Testing & Reports:"
	@echo "  make test             - Run unit tests only"
	@echo "  make test-all         - Run unit and integration tests"
	@echo "  make test-report      - Generate comprehensive test reports with coverage"
	@echo "  make view-report      - Open test reports in browser"
	@echo ""
	@echo "Application Build & Run:"
	@echo "  make build            - Build the application JAR"
	@echo "  make build-skip-tests - Build the application JAR skipping tests"
	@echo "  make run              - Run with default profile (dev)"
	@echo "  make run-dev          - Run JAR with DEV profile"
	@echo "  make run-prod         - Run JAR with PROD profile"
	@echo "  make dev              - Run with Maven in DEV profile (hot reload)"
	@echo "  make prod             - Run with Maven in PROD profile"
	@echo "  make build-run-dev    - Build and run in DEV profile"
	@echo "  make build-run-prod   - Build and run in PROD profile"
	@echo ""
	@echo "Docker Commands:"
	@echo "  make docker-build     - Build Docker image"
	@echo "  make docker-run       - Run application in Docker container"
	@echo "  make docker-stop      - Stop Docker container"
	@echo "  make docker-logs      - View Docker container logs"
	@echo "  make docker-up        - Start all services with docker-compose"
	@echo "  make docker-down      - Stop all services with docker-compose"
	@echo "  make docker-restart   - Restart all services"
	@echo "  make docker-clean     - Remove all containers and images"
	@echo ""
	@echo "Quick Start (Local Development):"
	@echo "  1. make postgres-up   - Start database"
	@echo "  2. sleep 5            - Wait for PostgreSQL to initialize"
	@echo "  3. make schema-up     - Apply schema"
	@echo "  4. make dev           - Run application with hot reload"
	@echo ""
	@echo "Quick Start (Docker):"
	@echo "  1. export FIXER_API_KEY=your_api_key"
	@echo "  2. make docker-up     - Start all services (DB + App)"
	@echo "  3. Visit http://localhost:8080/api/v1/rates/2024-01-15"
	@echo ""
	@echo "Production Deployment:"
	@echo "  1. Set environment variables:"
	@echo "     export DATABASE_URL=jdbc:postgresql://prod-server:5432/db"
	@echo "     export DATABASE_USERNAME=prod_user"
	@echo "     export DATABASE_PASSWORD=secure_password"
	@echo "     export FIXER_API_KEY=prod_key"
	@echo "  2. make build-run-prod"
	@echo ""
	@echo "==========================================\n"

# For local development PostgreSQL
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

build:
	mvn clean package

build-skip-tests:
	mvn clean package -DskipTests

# Run with default profile (dev)
run:
	java -jar target/testingday-exchange-rates-0.0.1-SNAPSHOT.jar

# Run with DEV profile (explicit)
run-dev:
	java -jar target/testingday-exchange-rates-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# Run with PROD profile
run-prod:
	java -jar target/testingday-exchange-rates-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# Run with Maven in DEV profile
dev:
	./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run with Maven in PROD profile
prod:
	./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# Build and run in DEV
build-run-dev: build run-dev

# Build and run in PROD
build-run-prod: build run-prod

# ========================================
# Testing & Test Reports
# ========================================

# Run unit tests only
test:
	./mvnw clean test

# Run unit and integration tests
test-all:
	./mvnw clean verify

# Generate comprehensive test reports with coverage
test-report:
	@echo "=========================================="
	@echo "Generating Test Reports"
	@echo "=========================================="
	./mvnw clean test verify site
	@echo ""
	@echo "=========================================="
	@echo "Test Reports Generated Successfully!"
	@echo "=========================================="
	@echo ""
	@echo "Reports available at:"
	@echo "  - Test Results:        target/site/surefire-report.html"
	@echo "  - Code Coverage:       target/site/jacoco/index.html"
	@echo "  - Full Site:           target/site/index.html"
	@echo ""
	@echo "Run 'make view-report' to open in browser"
	@echo ""

# Open test reports in browser
view-report:
	@echo "Opening test reports in browser..."
	open target/site/index.html || xdg-open target/site/index.html || echo "Please open target/site/index.html manually"

# ========================================
# Docker Commands
# ========================================

# Build Docker image
docker-build:
	docker build -t exchange-rates-app:latest .

# Run application in Docker (standalone)
docker-run:
	docker run -d \
	  --name exchange-rates-app \
	  -p 8080:8080 \
	  -e SPRING_PROFILES_ACTIVE=prod \
	  -e DATABASE_URL=${DATABASE_URL} \
	  -e DATABASE_USERNAME=${DATABASE_USERNAME} \
	  -e DATABASE_PASSWORD=${DATABASE_PASSWORD} \
	  -e FIXER_API_KEY=${FIXER_API_KEY} \
	  exchange-rates-app:latest

# Stop Docker container
docker-stop:
	docker stop exchange-rates-app || true
	docker rm exchange-rates-app || true

# View Docker container logs
docker-logs:
	docker logs -f exchange-rates-app

# Start all services with docker-compose
docker-up:
	docker compose up -d

# Stop all services with docker-compose
docker-down:
	docker compose down

# Restart all services
docker-restart: docker-down docker-up

# Clean up all containers, images, and volumes
docker-clean:
	docker compose down -v
	docker rmi exchange-rates-app:latest || true
	docker volume prune -f
