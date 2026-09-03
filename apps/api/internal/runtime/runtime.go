package runtime

import (
	"os"

	"github.com/wastingnotime/cat-care/apps/api/internal/application"
	"github.com/wastingnotime/cat-care/apps/api/internal/httpapi"
	"github.com/wastingnotime/cat-care/apps/api/internal/infrastructure"
)

type Config struct{ Address string }

func LoadConfig() Config {
	address := os.Getenv("CAT_CARE_API_ADDR")
	if address == "" {
		address = ":8080"
	}
	return Config{Address: address}
}
func NewHandler() *httpapi.Server {
	repository := infrastructure.NewMultiCatMemoryRepository("owner-local", "Mimi")
	service := application.NewService(repository, infrastructure.SystemClock{}, &infrastructure.SequenceIDs{})
	return httpapi.NewLocalServer(service)
}
