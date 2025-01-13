update groups set version = 0 where version is null;
update groups set additional_properties = '[]' where additional_properties is null;