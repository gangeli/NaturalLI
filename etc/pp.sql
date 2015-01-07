-- /u/nlp/packages/pgsql/bin/psql -h julie0 -p 4242 -U angeli angeli -f pp.sql

DROP TABLE IF EXISTS subj_obj_pp;
DROP TABLE IF EXISTS subj_pp_pp;
DROP TABLE IF EXISTS subj_pp;
DROP TABLE IF EXISTS subj_obj;
DROP TABLE IF EXISTS subj;
DROP TABLE IF EXISTS subj_obj_pp_check;
DROP TABLE IF EXISTS subj_pp_pp_check;
DROP TABLE IF EXISTS subj_pp_obj_check;
DROP TABLE IF EXISTS subj_pp_anyobj_check;
DROP TABLE IF EXISTS subj_pp_check;
DROP TABLE IF EXISTS obj_pp_check;
DROP TABLE IF EXISTS pp_pp_check;
DROP TABLE IF EXISTS pp_anyobj_check;
DROP TABLE IF EXISTS pp_check;

--
-- Instances of the (verb, subject, object, pp) quadruple
--
CREATE TABLE subj_obj_pp AS
  SELECT
    t.root_word    AS verb,
    t.child_1_word AS subj,
    t.child_2_word AS obj,
    t.child_3_edge AS pp,
    SUM(count)     AS count
  FROM
    syngrams_triedge t
  WHERE
    (t.child_1_edge = 'nsubj' OR t.child_1_edge = 'isubj' OR t.child_1_edge = 'nsubjpass') AND
    (t.child_2_edge = 'dobj' OR t.child_2_edge = 'iobj') AND
    t.child_3_edge LIKE 'prep_%'
  GROUP BY (verb, subj, obj, pp)
DISTRIBUTED BY (verb);

--
-- Instances of the (verb, subject, pp1, pp2) quadruple
--
CREATE TABLE subj_pp_pp AS
  SELECT
    t.root_word    AS verb,
    t.child_1_word AS subj,
    t.child_2_edge AS pp_other,
    t.child_3_edge AS pp,
    SUM(count)     AS count
  FROM
    syngrams_triedge t
  WHERE
    (t.child_1_edge = 'nsubj' OR t.child_1_edge = 'isubj' OR t.child_1_edge = 'nsubjpass') AND
    (t.child_2_edge LIKE 'prep_%') AND
    t.child_3_edge LIKE 'prep_%'
  GROUP BY (verb, subj, pp_other, pp)
DISTRIBUTED BY (verb);

--
-- Instances of the (verb, subject, pp) triple
--
CREATE TABLE subj_pp AS
  SELECT
    t.root_word    AS verb,
    t.child_1_word AS subj,
    t.child_2_edge AS pp,
    SUM(count)     AS count
  FROM
    syngrams_biedge t
  WHERE
    (t.child_1_edge = 'nsubj' OR t.child_1_edge = 'isubj' OR t.child_1_edge = 'nsubjpass') AND
    t.child_2_edge LIKE 'prep_%'
  GROUP BY (verb, subj, pp)
DISTRIBUTED BY (verb);

--
-- Instances of the (verb, subject, object) triple
--
CREATE TABLE subj_obj AS
  SELECT
    t.root_word    AS verb,
    t.child_1_word AS subj,
    t.child_2_word AS obj,
    SUM(count)     AS count
  FROM
    syngrams_biedge t
  WHERE
    (t.child_1_edge = 'nsubj' OR t.child_1_edge = 'isubj' OR t.child_1_edge = 'nsubjpass') AND
    (t.child_2_edge = 'dobj' OR t.child_2_edge = 'iobj')
  GROUP BY (verb, subj, obj)
DISTRIBUTED BY (verb);

--
-- Instances of the (verb, subject) triple
--
CREATE TABLE subj AS
  SELECT
    t.root_word    AS verb,
    t.child_1_word AS subj,
    SUM(count)     AS count
  FROM
    syngrams_edge t
  WHERE
    (t.child_1_edge = 'nsubj' OR t.child_1_edge = 'isubj' OR t.child_1_edge = 'nsubjpass')
  GROUP BY (verb, subj)
DISTRIBUTED BY (verb);


--
-- Probability of dropping a PP given (subj, obj)
--
CREATE TABLE subj_obj_pp_check AS
  SELECT
    yp.verb    AS verb,
    yp.subj    AS subj,
    yp.obj     AS obj,
    yp.pp      AS pp,
    yp.count::float / np.count::float AS percent,
    yp.count   AS positive_count
  FROM
    subj_obj_pp yp,
    subj_obj    np
  WHERE
    yp.verb = np.verb AND
    yp.subj = np.subj AND
    yp.obj  = np.obj
DISTRIBUTED BY (verb);

UPDATE subj_obj_pp_check SET percent=1.0 WHERE percent > 1.0;

CREATE TABLE obj_pp_check AS
  SELECT verb, obj, pp, 
         SUM(positive_count * percent) / SUM(positive_count) AS percent,
         SUM(positive_count)   AS positive_count
  FROM subj_obj_pp_check
  GROUP BY(verb, obj, pp)
DISTRIBUTED BY (verb);

--
-- Probability of dropping a PP given (subj, pp)
--
CREATE TABLE subj_pp_pp_check AS
  SELECT
    yp.verb     AS verb,
    yp.subj     AS subj,
    yp.pp_other AS pp_other,
    yp.pp       AS pp,
    yp.count::float / np.count::float AS percent,
    yp.count   AS positive_count
  FROM
    subj_pp_pp yp,
    subj_pp    np
  WHERE
    yp.verb      = np.verb AND
    yp.subj      = np.subj AND
    yp.pp_other  = np.pp
DISTRIBUTED BY (verb);

UPDATE subj_pp_pp_check SET percent=1.0 WHERE percent > 1.0;

CREATE TABLE pp_pp_check AS
  SELECT verb, pp_other, pp, 
         SUM(positive_count * percent) / SUM(positive_count) AS percent,
         SUM(positive_count)   AS positive_count
  FROM subj_pp_pp_check
  GROUP BY(verb, pp_other, pp)
DISTRIBUTED BY (verb);

--
-- Probability of dropping an obj given (subj, pp)
--
CREATE TABLE subj_pp_obj_check AS
  SELECT
    yp.verb     AS verb,
    yp.subj     AS subj,
    yp.pp       AS pp,
    yp.obj      AS obj,
    yp.count::float / np.count::float AS percent,
    yp.count   AS positive_count
  FROM
    subj_obj_pp yp,
    subj_pp     np
  WHERE
    yp.verb      = np.verb AND
    yp.subj      = np.subj AND
    yp.pp        = np.pp
DISTRIBUTED BY (verb);

UPDATE subj_pp_obj_check SET percent=1.0 WHERE percent > 1.0;

--
-- Probability of dropping any obj given (subj, pp)
--
CREATE TABLE subj_pp_anyobj_check AS
  SELECT
    verb         AS verb,
    subj         AS subj,
    pp           AS pp,
    SUM(positive_count * percent) / SUM(positive_count) AS percent,
    SUM(positive_count)   AS positive_count
  FROM
    subj_pp_obj_check
  GROUP BY (verb, subj, pp)
DISTRIBUTED BY (verb);

CREATE TABLE pp_anyobj_check AS
  SELECT verb, pp, 
         SUM(positive_count * percent) / SUM(positive_count) AS percent,
         SUM(positive_count)   AS positive_count
  FROM subj_pp_anyobj_check
  GROUP BY(verb, pp)
DISTRIBUTED BY (verb);

--
-- Probability of dropping a pp given (subj)
--
CREATE TABLE subj_pp_check AS
  SELECT
    yp.verb     AS verb,
    yp.subj     AS subj,
    yp.pp       AS pp,
    yp.count::float / np.count::float AS percent,
    yp.count   AS positive_count
  FROM
    subj_pp yp,
    subj    np
  WHERE
    yp.verb      = np.verb AND
    yp.subj      = np.subj
DISTRIBUTED BY (verb);

UPDATE subj_pp_check SET percent=1.0 WHERE percent > 1.0;

CREATE TABLE pp_check AS
  SELECT verb, pp,
         SUM(positive_count * percent) / SUM(positive_count) AS percent,
         SUM(positive_count)   AS positive_count
  FROM subj_pp_check
  GROUP BY(verb, pp)
DISTRIBUTED BY (verb);
