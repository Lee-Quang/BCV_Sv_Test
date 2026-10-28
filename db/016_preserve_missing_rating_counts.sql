-- Preserve missing review counts instead of turning them into verified zeroes.
-- These 35 canonical IDs have blank Rating AND RatingCount in the reviewed CSV.
-- Only untouched zero/null pairs from the original model import are backfilled;
-- a rating or a positive count entered afterwards is preserved. Safe to rerun.
BEGIN;

ALTER TABLE public.place ALTER COLUMN rating_count DROP NOT NULL;
ALTER TABLE public.place ALTER COLUMN rating_count DROP DEFAULT;

UPDATE public.place p
SET rating_count = NULL
WHERE p.rating IS NULL AND p.rating_count = 0
  AND p.place_id IN (
    3, 14, 19, 26, 39, 62, 65, 81, 90, 101, 115, 117, 122, 160, 162,
    168, 175, 200, 224, 225, 226, 231, 233, 238, 239, 241, 246, 270,
    280, 296, 299, 306, 307, 311, 313
  )
  AND EXISTS (
    SELECT 1 FROM public.place_feature pf
    JOIN public.recommendation_config rc USING (recommendation_config_id)
    WHERE pf.place_id = p.place_id AND rc.version = 'rank_20261020_draft'
  );

COMMIT;
