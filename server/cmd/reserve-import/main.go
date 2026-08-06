package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"

	"reserve-sin/server/internal/database"
	"reserve-sin/server/internal/importer"
)

func main() {
	inputPath := flag.String("input", "", "path to the financial-movement CSV")
	sourceID := flag.String("source-id", "finance-movement-2026", "stable identifier for idempotent import")
	flag.Parse()
	if *inputPath == "" {
		log.Fatal("-input is required")
	}
	file, err := os.Open(*inputPath)
	if err != nil {
		log.Fatal(err)
	}
	defer file.Close()

	databasePath, err := database.PathFromCurrentEnvironment()
	if err != nil {
		log.Fatal(err)
	}
	db, err := database.Open(context.Background(), databasePath)
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()
	if err := database.ApplyMigrations(context.Background(), db); err != nil {
		log.Fatal(err)
	}
	result, err := importer.ImportCSV(context.Background(), db, file, *sourceID)
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("imported: categories=%d labels=%d transactions=%d skipped=%d\n", result.CategoriesCreated, result.LabelsCreated, result.TransactionsCreated, result.TransactionsSkipped)
}
