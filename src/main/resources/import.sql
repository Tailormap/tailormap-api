
insert into groups(name, system_group, description) values ('admin', true, 'Administrators with full access');
insert into groups(name, system_group, description) values ('admin-catalog', true, 'Users authorized to edit the catalog');
insert into groups(name, system_group, description) values ('admin-users', true, 'Users authorized to create and edit user accounts');
insert into groups(name, system_group, description) values ('admin-applications', true, 'Users authorized to edit applications');

-- Default admin account created with StartupAdminAccountCreator
