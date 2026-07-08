from __future__ import annotations

from locust import HttpUser, between, task

from auth import login
from config import MAX_ORDERS_PER_USER, UNIT_PASSWORD, claim_unit_username
from environment_guard import verify_environment
from scenarios import list_orders, list_products, submit_small_order


def product_items(response):
    body = response.json()
    if isinstance(body, dict):
        return body.get("items", [])
    if isinstance(body, list):
        return body
    return []


class UnitProcurementUser(HttpUser):
    abstract = True
    wait_time = between(1, 5)

    def on_start(self):
        verify_environment(self.client)
        self.username = claim_unit_username()
        self.token = login(self.client, self.username, UNIT_PASSWORD)
        self.product_cache = []
        self.created_orders = 0
        self.max_orders = MAX_ORDERS_PER_USER

    @task(20)
    def browse_products(self):
        response = list_products(self.client, self.token)
        if response.status_code == 200:
            self.product_cache = product_items(response)

    @task(15)
    def orders(self):
        list_orders(self.client, self.token)

    @task(5)
    def create_order(self):
        if self.created_orders >= self.max_orders:
            return
        if not self.product_cache:
            response = list_products(self.client, self.token)
            if response.status_code == 200:
                self.product_cache = product_items(response)
        response = submit_small_order(self.client, self.token, self.product_cache)
        if response is not None and response.status_code in {200, 201}:
            self.created_orders += 1
