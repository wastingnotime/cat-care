package main

import (
	"log"
	"net/http"

	catruntime "github.com/wastingnotime/cat-care/apps/api/internal/runtime"
)

func main() {
	config := catruntime.LoadConfig()
	log.Printf("cat care api listening on %s", config.Address)
	if err := http.ListenAndServe(config.Address, catruntime.NewHandler().Handler()); err != nil {
		log.Fatal(err)
	}
}
