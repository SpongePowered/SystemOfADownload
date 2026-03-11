package domain

type Group struct {
	GroupID string
	Name    string
	Website *string
}

type Artifact struct {
	GroupID         string
	ArtifactID      string
	DisplayName     string
	Website         *string
	GitRepositories []string
	Issues          *string
}
