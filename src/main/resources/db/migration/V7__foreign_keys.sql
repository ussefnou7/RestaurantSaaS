-- =====================================================================
-- Foreign key constraints (all modules) - added last
-- =====================================================================

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT branches_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.document_history
    ADD CONSTRAINT document_history_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.employee_leave_balances
    ADD CONSTRAINT employee_leave_balances_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);
ALTER TABLE ONLY public.employee_leave_balances
    ADD CONSTRAINT employee_leave_balances_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES public.hr_employees(id);
ALTER TABLE ONLY public.employee_leave_balances
    ADD CONSTRAINT employee_leave_balances_leave_type_id_fkey FOREIGN KEY (leave_type_id) REFERENCES public.hr_leave_type(id);
ALTER TABLE ONLY public.employee_leave_balances
    ADD CONSTRAINT employee_leave_balances_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.employee_salaries
    ADD CONSTRAINT employee_salaries_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);
ALTER TABLE ONLY public.employee_salaries
    ADD CONSTRAINT employee_salaries_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES public.hr_employees(id);
ALTER TABLE ONLY public.employee_salaries
    ADD CONSTRAINT employee_salaries_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.employee_salary_adjustments
    ADD CONSTRAINT employee_salary_adjustments_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);
ALTER TABLE ONLY public.employee_salary_adjustments
    ADD CONSTRAINT employee_salary_adjustments_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES public.hr_employees(id);
ALTER TABLE ONLY public.employee_salary_adjustments
    ADD CONSTRAINT employee_salary_adjustments_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.inventory_transaction
    ADD CONSTRAINT fk_inv_tx_reverses FOREIGN KEY (reverses_transaction_id) REFERENCES public.inventory_transaction(id);
ALTER TABLE ONLY public.material_catalog
    ADD CONSTRAINT fk_material_catalog_default_display_uom FOREIGN KEY (default_display_uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.material
    ADD CONSTRAINT fk_material_display_uom FOREIGN KEY (display_uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.order_consumption_event
    ADD CONSTRAINT fk_oce_material FOREIGN KEY (material_id) REFERENCES public.material(id);
ALTER TABLE ONLY public.order_consumption_event
    ADD CONSTRAINT fk_oce_posted_tx FOREIGN KEY (posted_transaction_id) REFERENCES public.inventory_transaction(id);
ALTER TABLE ONLY public.order_consumption_event
    ADD CONSTRAINT fk_oce_reverses_event FOREIGN KEY (reverses_event_id) REFERENCES public.order_consumption_event(id);
ALTER TABLE ONLY public.order_consumption_event
    ADD CONSTRAINT fk_oce_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.order_consumption_event
    ADD CONSTRAINT fk_oce_uom FOREIGN KEY (uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.order_consumption_event
    ADD CONSTRAINT fk_oce_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.physical_count_line
    ADD CONSTRAINT fk_pc_line_adjustment_tx FOREIGN KEY (adjustment_transaction_id) REFERENCES public.inventory_transaction(id);
ALTER TABLE ONLY public.physical_count_line
    ADD CONSTRAINT fk_pc_line_count FOREIGN KEY (physical_count_id) REFERENCES public.physical_count(id);
ALTER TABLE ONLY public.physical_count_line
    ADD CONSTRAINT fk_pc_line_material FOREIGN KEY (material_id) REFERENCES public.material(id);
ALTER TABLE ONLY public.physical_count_line
    ADD CONSTRAINT fk_pc_line_uom FOREIGN KEY (uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.physical_count
    ADD CONSTRAINT fk_physical_count_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.inventory_transfer
    ADD CONSTRAINT fk_transfer_dest_warehouse FOREIGN KEY (destination_warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.inventory_transfer_line
    ADD CONSTRAINT fk_transfer_line_dispatch_tx FOREIGN KEY (dispatch_transaction_id) REFERENCES public.inventory_transaction(id);
ALTER TABLE ONLY public.inventory_transfer_line
    ADD CONSTRAINT fk_transfer_line_material FOREIGN KEY (material_id) REFERENCES public.material(id);
ALTER TABLE ONLY public.inventory_transfer_line
    ADD CONSTRAINT fk_transfer_line_receive_tx FOREIGN KEY (receive_transaction_id) REFERENCES public.inventory_transaction(id);
ALTER TABLE ONLY public.inventory_transfer_line
    ADD CONSTRAINT fk_transfer_line_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.inventory_transfer_line
    ADD CONSTRAINT fk_transfer_line_transfer FOREIGN KEY (transfer_id) REFERENCES public.inventory_transfer(id);
ALTER TABLE ONLY public.inventory_transfer_line
    ADD CONSTRAINT fk_transfer_line_uom FOREIGN KEY (uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.inventory_transfer
    ADD CONSTRAINT fk_transfer_source_warehouse FOREIGN KEY (source_warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.inventory_transfer
    ADD CONSTRAINT fk_transfer_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.uom
    ADD CONSTRAINT fk_uom_base_uom FOREIGN KEY (base_uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.uom
    ADD CONSTRAINT fk_uom_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.hr_employees
    ADD CONSTRAINT hr_employees_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);
ALTER TABLE ONLY public.hr_employees
    ADD CONSTRAINT hr_employees_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.jobs(id);
ALTER TABLE ONLY public.hr_employees
    ADD CONSTRAINT hr_employees_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.hr_employees
    ADD CONSTRAINT hr_employees_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.hr_leave_request
    ADD CONSTRAINT hr_leave_request_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);
ALTER TABLE ONLY public.hr_leave_request
    ADD CONSTRAINT hr_leave_request_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES public.hr_employees(id);
ALTER TABLE ONLY public.hr_leave_request
    ADD CONSTRAINT hr_leave_request_leave_balance_id_fkey FOREIGN KEY (leave_balance_id) REFERENCES public.employee_leave_balances(id);
ALTER TABLE ONLY public.hr_leave_request
    ADD CONSTRAINT hr_leave_request_leave_type_id_fkey FOREIGN KEY (leave_type_id) REFERENCES public.hr_leave_type(id);
ALTER TABLE ONLY public.hr_leave_request
    ADD CONSTRAINT hr_leave_request_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.hr_leave_type
    ADD CONSTRAINT hr_leave_type_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.inventory_transaction
    ADD CONSTRAINT inventory_transaction_entered_uom_id_fkey FOREIGN KEY (entered_uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.inventory_transaction
    ADD CONSTRAINT inventory_transaction_material_id_fkey FOREIGN KEY (material_id) REFERENCES public.material(id);
ALTER TABLE ONLY public.inventory_transaction
    ADD CONSTRAINT inventory_transaction_stock_uom_id_fkey FOREIGN KEY (stock_uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.inventory_transaction
    ADD CONSTRAINT inventory_transaction_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.inventory_transaction
    ADD CONSTRAINT inventory_transaction_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.material_catalog
    ADD CONSTRAINT material_catalog_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.material_category(id);
ALTER TABLE ONLY public.material_catalog
    ADD CONSTRAINT material_catalog_default_uom_id_fkey FOREIGN KEY (default_stock_uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.material
    ADD CONSTRAINT material_catalog_id_fkey FOREIGN KEY (catalog_id) REFERENCES public.material_catalog(id);
ALTER TABLE ONLY public.material
    ADD CONSTRAINT material_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.material_category(id);
ALTER TABLE ONLY public.material_category
    ADD CONSTRAINT material_category_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.material
    ADD CONSTRAINT material_default_uom_id_fkey FOREIGN KEY (stock_uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.material
    ADD CONSTRAINT material_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.physical_count_line
    ADD CONSTRAINT physical_count_line_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.physical_count_line
    ADD CONSTRAINT physical_count_line_waste_transaction_id_fkey FOREIGN KEY (waste_transaction_id) REFERENCES public.inventory_transaction(id);
ALTER TABLE ONLY public.physical_count
    ADD CONSTRAINT physical_count_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.purchase_invoice_line
    ADD CONSTRAINT purchase_invoice_line_material_id_fkey FOREIGN KEY (material_id) REFERENCES public.material(id);
ALTER TABLE ONLY public.purchase_invoice_line
    ADD CONSTRAINT purchase_invoice_line_purchase_invoice_id_fkey FOREIGN KEY (purchase_invoice_id) REFERENCES public.purchase_invoice(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.purchase_invoice_line
    ADD CONSTRAINT purchase_invoice_line_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.purchase_invoice
    ADD CONSTRAINT purchase_invoice_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.supplier(id);
ALTER TABLE ONLY public.purchase_invoice
    ADD CONSTRAINT purchase_invoice_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.purchase_invoice
    ADD CONSTRAINT purchase_invoice_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.purchase_return_line
    ADD CONSTRAINT purchase_return_line_material_id_fkey FOREIGN KEY (material_id) REFERENCES public.material(id);
ALTER TABLE ONLY public.purchase_return_line
    ADD CONSTRAINT purchase_return_line_original_line_id_fkey FOREIGN KEY (original_line_id) REFERENCES public.purchase_invoice_line(id);
ALTER TABLE ONLY public.purchase_return_line
    ADD CONSTRAINT purchase_return_line_purchase_return_id_fkey FOREIGN KEY (purchase_return_id) REFERENCES public.purchase_return(id);
ALTER TABLE ONLY public.purchase_return_line
    ADD CONSTRAINT purchase_return_line_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.purchase_return
    ADD CONSTRAINT purchase_return_original_invoice_id_fkey FOREIGN KEY (original_invoice_id) REFERENCES public.purchase_invoice(id);
ALTER TABLE ONLY public.purchase_return
    ADD CONSTRAINT purchase_return_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES public.supplier(id);
ALTER TABLE ONLY public.purchase_return
    ADD CONSTRAINT purchase_return_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id);
ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);
ALTER TABLE ONLY public.stock_balance
    ADD CONSTRAINT stock_balance_material_id_fkey FOREIGN KEY (material_id) REFERENCES public.material(id);
ALTER TABLE ONLY public.stock_balance
    ADD CONSTRAINT stock_balance_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.stock_balance
    ADD CONSTRAINT stock_balance_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES public.uom(id);
ALTER TABLE ONLY public.stock_balance
    ADD CONSTRAINT stock_balance_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);
ALTER TABLE ONLY public.stock_batch
    ADD CONSTRAINT stock_batch_source_transaction_id_fkey FOREIGN KEY (source_transaction_id) REFERENCES public.inventory_transaction(id);
ALTER TABLE ONLY public.stock_batch
    ADD CONSTRAINT stock_batch_stock_balance_id_fkey FOREIGN KEY (stock_balance_id) REFERENCES public.stock_balance(id);
ALTER TABLE ONLY public.stock_batch
    ADD CONSTRAINT stock_batch_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.supplier
    ADD CONSTRAINT supplier_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT user_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id);
ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT user_permissions_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.user_permissions
    ADD CONSTRAINT user_permissions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);
ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
ALTER TABLE ONLY public.warehouse
    ADD CONSTRAINT warehouse_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);
ALTER TABLE ONLY public.warehouse
    ADD CONSTRAINT warehouse_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
