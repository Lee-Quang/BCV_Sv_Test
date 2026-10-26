-- BCV: tuned ranking weights for the recommendation config (design 3.5.4.6, step 7).
-- The design's initial weights (P 0.4, C 0.2, A 0.2, R 0.1, N 0.1) let well-known far places
-- outrank nearby ones for low-activity users. With κ = 0.1 in the step conversion, a heavier
-- activity weight keeps the effort a user can manage in front:
--   PREFERENCE 0.35, CLUSTER 0.15, ACTIVITY 0.30, RATING 0.10, POPULARITY 0.10 (sum 1).
-- Also marks this config ACTIVE (the design reads the weights of the configuration in effect).
-- One transaction; stops if the config is not in the expected state; safe to run again.

BEGIN;

DO $$
DECLARE
    config_id bigint;
BEGIN
    SELECT recommendation_config_id INTO config_id
    FROM public.recommendation_config WHERE version = 'rank_20261020_draft';
    IF config_id IS NULL THEN
        RAISE EXCEPTION 'Recommendation config rank_20261020_draft not found; no changes applied';
    END IF;
    IF (SELECT weight_value FROM public.recommendation_weight w
        JOIN public.recommendation_criterion c ON c.criterion_id = w.criterion_id
        WHERE w.recommendation_config_id = config_id AND c.code = 'ACTIVITY') = 0.30 THEN
        -- Weights done earlier; still make sure this config is the one in effect.
        UPDATE public.recommendation_config SET status = CASE
            WHEN recommendation_config_id = config_id THEN 'ACTIVE' ELSE 'INACTIVE' END
        WHERE status = 'ACTIVE' OR recommendation_config_id = config_id;
        RAISE NOTICE 'Tuned weights already applied; config is ACTIVE.';
        RETURN;
    END IF;

    UPDATE public.recommendation_weight w
    SET weight_value = v.weight
    FROM public.recommendation_criterion c
    JOIN (VALUES ('PREFERENCE', 0.35), ('CLUSTER', 0.15), ('ACTIVITY', 0.30),
                 ('RATING', 0.10), ('POPULARITY', 0.10)) AS v(code, weight) ON v.code = c.code
    WHERE w.criterion_id = c.criterion_id AND w.recommendation_config_id = config_id;

    -- The design reads weights from the configuration in effect: activate this one (only one ACTIVE).
    UPDATE public.recommendation_config SET status = 'INACTIVE'
    WHERE status = 'ACTIVE' AND recommendation_config_id <> config_id;
    UPDATE public.recommendation_config SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
    WHERE recommendation_config_id = config_id;

    IF (SELECT count(*) FROM public.recommendation_weight WHERE recommendation_config_id = config_id) <> 5
       OR (SELECT sum(weight_value) FROM public.recommendation_weight WHERE recommendation_config_id = config_id) <> 1 THEN
        RAISE EXCEPTION 'Weights must be five criteria summing to 1; transaction rolled back';
    END IF;
END $$;

COMMIT;

SELECT c.code, w.weight_value
FROM public.recommendation_weight w
JOIN public.recommendation_criterion c ON c.criterion_id = w.criterion_id
ORDER BY w.weight_value DESC, c.code;
