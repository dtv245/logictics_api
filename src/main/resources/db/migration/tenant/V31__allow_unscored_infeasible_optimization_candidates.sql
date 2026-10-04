-- V30 is applied and remains unchanged. Infeasible candidates have no score,
-- not a fabricated zero. Its existing conditional CHECK still requires a score
-- and rank for feasible candidates and NULL score/rank for rejected candidates.
ALTER TABLE optimization_assignments ALTER COLUMN final_score DROP NOT NULL;
ALTER TABLE optimization_assignments ALTER COLUMN final_score DROP DEFAULT;
