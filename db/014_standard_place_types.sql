-- BCV: replace the 10 imported place types with 12 standard ones (ids 101-112).
-- Run after 001-013. One transaction; it stops without changes if the database is not in the
-- expected state, and it can be run again safely (it does nothing once applied).
--
-- Each new type is defined by model features (the 35-feature K-Means data in place_feature_value):
-- a place matches a type when it has ANY of that type's features. The backend uses the same table
-- (RecommendationScoring.PlaceTypeFeatures) for the preference score P.
--
--  id  type                         features
-- 101  Di tích và bảo tàng          kind__heritage, kind__museum
-- 102  Đền, chùa và nhà thờ         kind__religious
-- 103  Công trình kiến trúc         kind__structure
-- 104  Phố cổ và làng nghề          kind__community, kind__craft
-- 105  Chợ và ẩm thực               kind__market, theme__cuisine
-- 106  Vui chơi và nghỉ dưỡng       kind__recreation
-- 107  Đồng quê và nông nghiệp      kind__agriculture
-- 108  Biển và đảo                  landform__coast, landform__island, landform__lagoon, landform__dunes
-- 109  Sông, hồ và thác             landform__river, landform__lake, landform__waterfall, landform__stream
-- 110  Núi và đèo                   landform__mountain, landform__valley
-- 111  Hang động                    landform__cave
-- 112  Rừng và khu bảo tồn          ecosystem__forest, ecosystem__flooded_habitat, ecosystem__grassland
--
-- Main type of each place: cultural places keep their current kind under its new name. The
-- former "Thiên nhiên" places are split by landform in the priority cave > coast/island >
-- river/lake/waterfall > mountain > forest; nature places with no landform tag go to 112.
--
-- Saved interests move to the new type of the same kind. "Thiên nhiên" cannot move (it now spans
-- five types and a user picks at most three), so it is dropped; a user left with no interest is
-- asked to choose again by the app at the next sign-in.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.place_type WHERE place_type_id = 101) THEN
        RAISE NOTICE 'Standard place types already applied; nothing to do.';
        RETURN;
    END IF;
    IF (SELECT count(*) FROM public.place_type WHERE name IN (
            'Thiên nhiên', 'Di tích lịch sử', 'Tôn giáo và tín ngưỡng', 'Bảo tàng',
            'Công trình và không gian xây dựng', 'Chợ', 'Làng nghề và thủ công',
            'Cảnh quan nông nghiệp', 'Làng, bản và phố cổ', 'Khu vui chơi và nghỉ dưỡng')) <> 10
       OR (SELECT count(*) FROM public.place_type) <> 10 THEN
        RAISE EXCEPTION 'Unexpected place_type rows; no changes applied';
    END IF;

    INSERT INTO public.place_type (place_type_id, name) VALUES
        (101, 'Di tích và bảo tàng'),
        (102, 'Đền, chùa và nhà thờ'),
        (103, 'Công trình kiến trúc'),
        (104, 'Phố cổ và làng nghề'),
        (105, 'Chợ và ẩm thực'),
        (106, 'Vui chơi và nghỉ dưỡng'),
        (107, 'Đồng quê và nông nghiệp'),
        (108, 'Biển và đảo'),
        (109, 'Sông, hồ và thác'),
        (110, 'Núi và đèo'),
        (111, 'Hang động'),
        (112, 'Rừng và khu bảo tồn');

    CREATE TEMP TABLE old_type_map (old_name text PRIMARY KEY, new_id bigint) ON COMMIT DROP;
    INSERT INTO old_type_map VALUES
        ('Di tích lịch sử', 101), ('Bảo tàng', 101), ('Tôn giáo và tín ngưỡng', 102),
        ('Công trình và không gian xây dựng', 103), ('Làng, bản và phố cổ', 104),
        ('Làng nghề và thủ công', 104), ('Chợ', 105), ('Khu vui chơi và nghỉ dưỡng', 106),
        ('Cảnh quan nông nghiệp', 107), ('Thiên nhiên', NULL);

    -- Features with value 1 per place.
    CREATE TEMP TABLE place_codes ON COMMIT DROP AS
    SELECT pf.place_id, array_agg(c.code) AS codes
    FROM public.place_feature pf
    JOIN public.place_feature_value v ON v.place_feature_id = pf.place_feature_id AND v.feature_value = 1
    JOIN public.recommendation_criterion c ON c.criterion_id = v.criterion_id
    GROUP BY pf.place_id;

    UPDATE public.place p
    SET place_type_id = COALESCE(m.new_id, (
        SELECT CASE
            WHEN pc.codes && ARRAY['landform__cave']::varchar[] THEN 111
            WHEN pc.codes && ARRAY['landform__coast', 'landform__island', 'landform__lagoon', 'landform__dunes']::varchar[] THEN 108
            WHEN pc.codes && ARRAY['landform__river', 'landform__lake', 'landform__waterfall', 'landform__stream']::varchar[] THEN 109
            WHEN pc.codes && ARRAY['landform__mountain', 'landform__valley']::varchar[] THEN 110
            ELSE 112
        END
        FROM (SELECT (SELECT codes FROM place_codes WHERE place_id = p.place_id) AS codes) pc))
    FROM public.place_type t
    JOIN old_type_map m ON m.old_name = t.name
    WHERE t.place_type_id = p.place_type_id;

    INSERT INTO public.user_preference_place_type (user_preference_id, place_type_id)
    SELECT DISTINCT up.user_preference_id, m.new_id
    FROM public.user_preference_place_type up
    JOIN public.place_type t ON t.place_type_id = up.place_type_id
    JOIN old_type_map m ON m.old_name = t.name
    WHERE m.new_id IS NOT NULL
    ON CONFLICT DO NOTHING;
    DELETE FROM public.user_preference_place_type WHERE place_type_id < 101;
    DELETE FROM public.place_type WHERE place_type_id < 101;

    PERFORM setval(pg_get_serial_sequence('public.place_type', 'place_type_id'),
                   (SELECT max(place_type_id) FROM public.place_type));

    IF (SELECT count(*) FROM public.place_type) <> 12
       OR EXISTS (SELECT 1 FROM public.place WHERE place_type_id NOT BETWEEN 101 AND 112)
       OR (SELECT count(*) FROM public.place) <> (SELECT count(*) FROM public.place WHERE place_type_id >= 101) THEN
        RAISE EXCEPTION 'Standard place type check failed; transaction rolled back';
    END IF;
END $$;

COMMIT;

SELECT t.place_type_id, t.name, count(p.place_id) AS places
FROM public.place_type t LEFT JOIN public.place p ON p.place_type_id = t.place_type_id
GROUP BY t.place_type_id, t.name ORDER BY t.place_type_id;
