update application
set content_root = jsonb_set(
    content_root,
    '{layerNodes}',
    coalesce(
        (
            select jsonb_agg(
               case
                   when node ->> 'objectType' = 'AppTreeLevelNode' then
                       jsonb_set(
                           node,
                           '{expandOnStartup}',
                           to_jsonb(
                               case
                                   when node -> 'expandOnStartup' = 'true'::jsonb then 'alwaysExpand'
                                   when node -> 'expandOnStartup' = 'false'::jsonb then 'neverExpand'
                                   else 'automatic'
                                   end
                           ),
                           true
                       )
                   else node
                   end
                   )
            from jsonb_array_elements(coalesce(content_root -> 'layerNodes', '[]'::jsonb)) as node
        ),
        '[]'::jsonb
    ),
    true
)
where content_root ? 'layerNodes';