-- One database per service (they never read each other's tables). Runs once, when the data volume is first created.
CREATE DATABASE authdb;
CREATE DATABASE itemdb;
CREATE DATABASE inventorydb;
CREATE DATABASE taskdb;
CREATE DATABASE notificationdb;
