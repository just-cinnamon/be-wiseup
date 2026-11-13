import { defineRailway, github, project, service } from "railway/iac";
// This repository manages only its own resources in the environment. Other
// repositories export their own partial name.
// See https://docs.railway.com/infrastructure-as-code#multi-repo-projects
export const partial = "be-wiseup";
export default defineRailway(() => {
  const beWiseup = service("be-wiseup", {
    source: github("just-cinnamon/be-wiseup", { branch: "develop" }),
    build: { buildEnvironment: "V3", builder: "DOCKERFILE", dockerfilePath: "Dockerfile" },
    healthcheck: "/api/health/live",
    healthcheckTimeout: 300,
    replicas: { "europe-west4-drams3a": 1 },
  });

  return project("content-tenderness", {
    variables: { managed: false },
    resources: [beWiseup],
  });
});
