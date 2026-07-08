from admin_user import AdminProcurementUser
from unit_user import UnitProcurementUser


class UnitUser(UnitProcurementUser):
    weight = 7


class AdminUser(AdminProcurementUser):
    weight = 3
