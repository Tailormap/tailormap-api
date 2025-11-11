update feature_type as ft
set settings = (ft.settings - 'readOnlyAttributes')
    || jsonb_build_object(
                       'editableAttributes',
                       coalesce((
                                   select to_jsonb(array_agg(attr->> 'name'))
                                   from jsonb_array_elements(COALESCE(ft.attributes, '[]'::jsonb)) as attr
                                   where not exists (
                                       select 1
                                       from jsonb_array_elements_text(COALESCE(ft.settings -> 'readOnlyAttributes', '[]'::jsonb)) as name
                                       where name = (attr ->> 'name')
                                   )
                       ), '[]'::jsonb)
    )
