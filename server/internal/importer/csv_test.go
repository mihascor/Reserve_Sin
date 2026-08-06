package importer

import (
	"context"
	"strings"
	"testing"

	"reserve-sin/server/internal/database"
)

const testCSV = `"Счет ""Подушка"" движение средств",,,,,
Дата,Категории,Подушка,Катя,Пусто 1
,10 000 р.,7 000 р.,3 000 р.,
06.06.2026,   ---,5 000 р.,2 000 р.,
07.06.2026,Погашен долг,-500 р.,,
`

func TestImportCSVCreatesHistoryAndIsIdempotent(t *testing.T) {
	ctx := context.Background()
	db, err := database.Open(ctx, ":memory:")
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	if err := database.ApplyMigrations(ctx, db); err != nil {
		t.Fatal(err)
	}

	result, err := ImportCSV(ctx, db, strings.NewReader(testCSV), "test-import")
	if err != nil {
		t.Fatal(err)
	}
	if result.CategoriesCreated != 2 || result.LabelsCreated != 1 || result.TransactionsCreated != 3 {
		t.Fatalf("unexpected first result: %+v", result)
	}
	var total int64
	if err := db.QueryRow(`SELECT COALESCE(SUM(amount_rub), 0) FROM transactions`).Scan(&total); err != nil {
		t.Fatal(err)
	}
	if total != 6_500 {
		t.Fatalf("total = %d, want 6500", total)
	}

	second, err := ImportCSV(ctx, db, strings.NewReader(testCSV), "test-import")
	if err != nil {
		t.Fatal(err)
	}
	if second.TransactionsCreated != 0 || second.TransactionsSkipped != 3 {
		t.Fatalf("unexpected repeated result: %+v", second)
	}
}
