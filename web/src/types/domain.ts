export type Role = 'admin' | 'unit_user';
export type OrderStatus = 'pending' | 'accepted' | 'preparing' | 'shipped' | 'completed' | 'cancelled';
export type SupplyStatus = 'normal' | 'tight' | 'paused' | 'off_shelf';

export interface UserProfile {
  id: string;
  username: string;
  display_name: string;
  role: Role;
  unit_id: string;
  unit_code: string;
  unit_name: string;
  default_delivery_point: string;
  active: boolean;
  must_change_password: boolean;
  last_login_at?: string;
}

export interface Unit {
  id: string;
  unit_code: string;
  unit_name: string;
  default_delivery_point: string;
  address_note?: string;
  active: boolean | number;
  account_count?: number;
  order_count?: number;
  created_at?: string;
  updated_at?: string;
}

export interface Product {
  id: string;
  product_code: string;
  name: string;
  category: string;
  spec: string;
  unit: string;
  price_cents: number;
  stock_quantity: string;
  reserved_quantity: string;
  available_quantity?: string;
  min_order_quantity: string;
  quantity_step: string;
  warning_quantity: string;
  origin?: string;
  supplier?: string;
  shelf_life?: string;
  storage_method?: string;
  description?: string;
  image_path?: string;
  supply_status: SupplyStatus;
  active: boolean | number;
  is_deleted?: boolean | number;
  updated_at?: string;
}

export interface CartLine {
  product: Product;
  quantity: number;
}

export interface OrderItem {
  id: string;
  product_id: string;
  product_name_snapshot: string;
  product_code_snapshot?: string;
  category_snapshot?: string;
  spec_snapshot: string;
  unit_snapshot: string;
  price_cents_snapshot: number;
  quantity: string;
  requested_quantity?: string;
  actual_quantity?: string;
  subtotal_cents: number;
  adjustment_reason?: string;
  adjusted?: boolean;
}

export interface ShippingPhoto {
  id: string;
  url: string;
  thumbnail_url?: string;
  uploaded_at?: string;
  file_size?: number;
  width?: number;
  height?: number;
}

export interface ReceiptIssue {
  id: string;
  order_id: string;
  order_no?: string;
  unit_id?: string;
  unit_name?: string;
  issue_type: string;
  description: string;
  status: 'open' | 'resolved';
  reported_at?: string;
  resolution_note?: string;
  photos?: ShippingPhoto[];
}

export interface Order {
  id: string;
  order_no: string;
  unit_id: string;
  unit_name_snapshot: string;
  delivery_point_snapshot: string;
  note: string;
  status: OrderStatus;
  total_cents: number;
  created_at: string;
  accepted_at?: string;
  preparing_at?: string;
  shipped_at?: string;
  completed_at?: string;
  shipping_note?: string;
  items?: OrderItem[];
  shipping_photos?: ShippingPhoto[];
  receipt_issues?: ReceiptIssue[];
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  page_size: number;
}

export interface DashboardData {
  today_orders: number;
  today_total_cents: number;
  pending: number;
  preparing: number;
  shipped: number;
  tight_inventory: number;
  open_receipt_issues: number;
  recent_orders: Order[];
  demand_rank: Array<{ product_id: string; name: string; unit: string; quantity: number }>;
}

export type DashboardMetricKey =
  | 'today_valid_orders'
  | 'today_total_cents'
  | 'pending'
  | 'preparing'
  | 'waiting_shipment'
  | 'waiting_receipt'
  | 'open_receipt_issues'
  | 'tight_inventory';

export type DashboardTaskType = 'receipt_issues' | 'pending_orders' | 'waiting_shipment' | 'tight_inventory' | 'cutoff';

export interface DashboardOverview {
  business_date: string;
  server_now: string;
  refreshed_at: string;
  metrics: Record<DashboardMetricKey, number>;
  comparisons: {
    orders_vs_yesterday_percent: number;
    amount_vs_yesterday_percent: number;
  };
  tasks: Array<{
    type: DashboardTaskType;
    title: string;
    count: number;
    oldest_wait_seconds?: number | null;
    priority: 'critical' | 'high' | 'warning' | 'normal';
    description: string;
    action_label: string;
    target_url: string;
  }>;
  trend: Array<{ date: string; order_count: number; amount_cents: number }>;
  recent_orders: Array<Order & { item_count?: number; open_receipt_issue_count?: number }>;
  inventory_alerts: Array<{
    id: string;
    name: string;
    unit: string;
    stock_quantity: string;
    reserved_quantity: string;
    available_quantity: number;
    warning_quantity: string;
    supply_status: SupplyStatus;
    active: boolean | number;
  }>;
  demand_rank: Array<{ product_id: string; name: string; unit: string; quantity: number; unit_count: number; order_count: number }>;
  unit_rank: Array<{ unit_id: string; unit_name: string; order_count: number; amount_cents: number; product_count: number; issue_count: number }>;
  system_status: {
    service: string;
    last_backup_at: string;
    disk_usage_percent: number;
    version: string;
    refreshed_at: string;
  };
}
