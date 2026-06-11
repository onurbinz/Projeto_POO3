start container
- docker-compose up -d

modificar banco de dados:
- docker-compose exec -T postgres psql -U erp_admin -d erp_db -f /dev/stdin < docs/schema.sql