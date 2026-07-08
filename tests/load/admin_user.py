from __future__ import annotations

from locust import HttpUser, between, task

from auth import login
from config import ADMIN_PASSWORD, claim_admin_username
from environment_guard import verify_environment
from scenarios import admin_dashboard, admin_orders, admin_products


class AdminProcurementUser(HttpUser):
    abstract = True
    wait_time = between(1, 5)

    def on_start(self):
        verify_environment(self.client)
        self.username = claim_admin_username()
        self.token = login(self.client, self.username, ADMIN_PASSWORD)

    @task(20)
    def dashboard(self):
        admin_dashboard(self.client, self.token)

    @task(20)
    def orders(self):
        admin_orders(self.client, self.token)

    @task(10)
    def products(self):
        admin_products(self.client, self.token)
