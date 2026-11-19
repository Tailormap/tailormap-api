-- Clarify form field properties:
--   'type'     -> 'controlType': the edit control for the field, not the data type of the attribute. The values are
--                 renamed to controls too: 'boolean' -> 'checkbox', 'timestamp' -> 'datetime' and 'integer' -> 'number'
--                 (whether a number must be a whole number follows from the attribute type).
--   'disabled' -> 'readOnly':    the field is shown but its value can't be changed.
-- Renames the 'name' of a form tab to 'label', like the label of a form field.
-- Also removes the unused field property 'column' and form option 'columns'.
-- Renames the form option 'description' to 'adminComments': it is only shown in the admin, like the admin comments of an
-- application.
-- Replaces the choice list properties 'valueList', 'uniqueValuesAsOptions' and 'allowValueListOnly' of a field by a
-- 'choiceList' object: { source: 'list' | 'uniqueValues', values: [{ value, label }], allowFreeInput }. A choice list is
-- only created for a select field, or when choices were configured. 'allowValueListOnly' was only false when the admin
-- allowed free input, otherwise free input was not allowed.
--
-- Fields without the old properties are left untouched. If a field already has the new property it is kept and only the
-- old property is removed, so running this again has no effect.

create function pg_temp.form_field_rename_key(field jsonb, old_key text, new_key text) returns jsonb
    language sql
    immutable
as
$$
select case
           when field ? old_key then jsonb_build_object(new_key, field -> old_key) || (field - old_key)
           else field
           end
$$;

create function pg_temp.form_field_choice_list(field jsonb) returns jsonb
    language sql
    immutable
as
$$
select case
           when not (field ? 'valueList' or field ? 'uniqueValuesAsOptions' or field ? 'allowValueListOnly') then field
           when field ? 'choiceList'
               or not (field ->> 'controlType' = 'select'
                   or field -> 'uniqueValuesAsOptions' = 'true'::jsonb
                   or (jsonb_typeof(field -> 'valueList') = 'array' and jsonb_array_length(field -> 'valueList') > 0))
               then field - 'valueList' - 'uniqueValuesAsOptions' - 'allowValueListOnly'
           else (field - 'valueList' - 'uniqueValuesAsOptions' - 'allowValueListOnly') || jsonb_build_object(
                   'choiceList', jsonb_build_object(
                           'source', case when field -> 'uniqueValuesAsOptions' = 'true'::jsonb then 'uniqueValues' else 'list' end,
                           'values', case
                                         when jsonb_typeof(field -> 'valueList') = 'array' then field -> 'valueList'
                                         else '[]'::jsonb
                               end,
                           'allowFreeInput', coalesce(field -> 'allowValueListOnly' = 'false'::jsonb, false)))
           end
$$;

create function pg_temp.form_field_migrate(field jsonb) returns jsonb
    language sql
    immutable
as
$$
select case
           when f ->> 'controlType' in ('boolean', 'timestamp', 'integer') then
               f || jsonb_build_object('controlType',
                                       case f ->> 'controlType'
                                           when 'boolean' then 'checkbox'
                                           when 'timestamp' then 'datetime'
                                           when 'integer' then 'number'
                                           end)
           else f
           end
from (select pg_temp.form_field_choice_list(
                    pg_temp.form_field_rename_key(pg_temp.form_field_rename_key(field, 'type', 'controlType'),
                                                  'disabled', 'readOnly') - 'column') as f) as renamed
$$;

create function pg_temp.form_fields_migrate(fields jsonb) returns jsonb
    language sql
    immutable
as
$$
select case
           when jsonb_typeof(fields) = 'array' then
               (select coalesce(jsonb_agg(pg_temp.form_field_migrate(field) order by ordinality), '[]'::jsonb)
                from jsonb_array_elements(fields) with ordinality as e(field, ordinality))
           else fields
           end
$$;

create function pg_temp.form_options_migrate(options jsonb) returns jsonb
    language sql
    immutable
as
$$
select case
           when jsonb_typeof(options) <> 'object' then options
           when jsonb_typeof(options -> 'tabs') = 'array' then
               (pg_temp.form_field_rename_key(options, 'description', 'adminComments') - 'columns') || jsonb_build_object('tabs',
                                                           (select coalesce(jsonb_agg(
                                                                                    pg_temp.form_field_rename_key(tab, 'name', 'label')
                                                                                    order by ordinality), '[]'::jsonb)
                                                            from jsonb_array_elements(options -> 'tabs') with ordinality as e(tab, ordinality)))
           else pg_temp.form_field_rename_key(options, 'description', 'adminComments') - 'columns'
           end
$$;

update form
set fields  = pg_temp.form_fields_migrate(fields),
    options = pg_temp.form_options_migrate(options);

update history.form_revisions
set fields  = pg_temp.form_fields_migrate(fields),
    options = pg_temp.form_options_migrate(options);
