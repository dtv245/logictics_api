# JPA entity relationships

Tất cả quan hệ dùng `FetchType.LAZY` để tránh tải graph không cần thiết và hạn chế vòng tham chiếu khi serialize.

## AiDispatchSession

- Không có quan hệ foreign key.

## ApiKey

- Không có quan hệ foreign key.

## Customer

- Không có quan hệ foreign key.

## EldProviderConfiguration

- Không có quan hệ foreign key.

## LoadBoardConfiguration

- Không có quan hệ foreign key.

## Notification

- Không có quan hệ foreign key.

## TelegramChat

- Không có quan hệ foreign key.

## TenantRole

- Không có quan hệ foreign key.

## Terminal

- Không có quan hệ foreign key.

## AiDispatchDecision

- `session` → `AiDispatchSession`: `ManyToOne`, LAZY, bắt buộc.

## Container

- `currentTerminal` → `Terminal`: `ManyToOne`, LAZY, tùy chọn.

## CustomerUser

- `customer` → `Customer`: `ManyToOne`, LAZY, bắt buộc.

## Employee

- `role` → `TenantRole`: `ManyToOne`, LAZY, tùy chọn.

## HosLog

- `employee` → `Employee`: `ManyToOne`, LAZY, bắt buộc.

## HosViolation

- `employee` → `Employee`: `ManyToOne`, LAZY, bắt buộc.

## TenantRoleClaim

- `role` → `TenantRole`: `ManyToOne`, LAZY, bắt buộc.

## Truck

- `mainDriver` → `Employee`: `ManyToOne`, LAZY, tùy chọn.
- `secondaryDriver` → `Employee`: `ManyToOne`, LAZY, tùy chọn.

## DriverBehaviorEvent

- `employee` → `Employee`: `ManyToOne`, LAZY, bắt buộc.
- `reviewedBy` → `Employee`: `ManyToOne`, LAZY, tùy chọn.
- `truck` → `Truck`: `ManyToOne`, LAZY, tùy chọn.

## DriverHosStatus

- `employee` → `Employee`: `OneToOne`, LAZY, bắt buộc.

## EldDriverMapping

- `employee` → `Employee`: `ManyToOne`, LAZY, bắt buộc.

## EldVehicleMapping

- `truck` → `Truck`: `ManyToOne`, LAZY, bắt buộc.

## Expense

- `truck` → `Truck`: `ManyToOne`, LAZY, tùy chọn.
- `truckExpenseTruck` → `Truck`: `ManyToOne`, LAZY, tùy chọn.

## Load

- `container` → `Container`: `ManyToOne`, LAZY, tùy chọn.
- `customer` → `Customer`: `ManyToOne`, LAZY, bắt buộc.
- `assignedDispatcher` → `Employee`: `ManyToOne`, LAZY, tùy chọn.
- `destinationTerminal` → `Terminal`: `ManyToOne`, LAZY, tùy chọn.
- `originTerminal` → `Terminal`: `ManyToOne`, LAZY, tùy chọn.
- `assignedTruck` → `Truck`: `ManyToOne`, LAZY, tùy chọn.

## MaintenanceSchedule

- `truck` → `Truck`: `ManyToOne`, LAZY, bắt buộc.

## PostedTruck

- `truck` → `Truck`: `ManyToOne`, LAZY, bắt buộc.

## TrackingLink

- `load` → `Load`: `ManyToOne`, LAZY, bắt buộc.

## Trip

- `truck` → `Truck`: `ManyToOne`, LAZY, tùy chọn.

## AccidentReport

- `driver` → `Employee`: `ManyToOne`, LAZY, bắt buộc.
- `reviewedBy` → `Employee`: `ManyToOne`, LAZY, tùy chọn.
- `trip` → `Trip`: `ManyToOne`, LAZY, tùy chọn.
- `truck` → `Truck`: `ManyToOne`, LAZY, bắt buộc.

## AccidentThirdParty

- `accidentReport` → `AccidentReport`: `ManyToOne`, LAZY, bắt buộc.

## AccidentWitness

- `accidentReport` → `AccidentReport`: `ManyToOne`, LAZY, bắt buộc.

## Conversation

- `load` → `Load`: `ManyToOne`, LAZY, tùy chọn.

## DvirReport

- `driver` → `Employee`: `ManyToOne`, LAZY, bắt buộc.
- `reviewedBy` → `Employee`: `ManyToOne`, LAZY, tùy chọn.
- `trip` → `Trip`: `ManyToOne`, LAZY, tùy chọn.
- `truck` → `Truck`: `ManyToOne`, LAZY, bắt buộc.

## Invoice

- `customer` → `Customer`: `ManyToOne`, LAZY, tùy chọn.
- `employee` → `Employee`: `ManyToOne`, LAZY, tùy chọn.
- `load` → `Load`: `OneToOne`, LAZY, tùy chọn.

## LoadBoardListing

- `load` → `Load`: `ManyToOne`, LAZY, tùy chọn.

## LoadConditionReport

- `inspectedBy` → `Employee`: `ManyToOne`, LAZY, bắt buộc.
- `load` → `Load`: `ManyToOne`, LAZY, bắt buộc.

## LoadException

- `load` → `Load`: `ManyToOne`, LAZY, bắt buộc.

## MaintenanceRecord

- `performedBy` → `Employee`: `ManyToOne`, LAZY, tùy chọn.
- `maintenanceSchedule` → `MaintenanceSchedule`: `ManyToOne`, LAZY, tùy chọn.
- `truck` → `Truck`: `ManyToOne`, LAZY, bắt buộc.

## Message

- `conversation` → `Conversation`: `ManyToOne`, LAZY, bắt buộc.
- `sender` → `Employee`: `ManyToOne`, LAZY, bắt buộc.

## PaymentLink

- `invoice` → `Invoice`: `ManyToOne`, LAZY, bắt buộc.

## Payment

- `invoice` → `Invoice`: `ManyToOne`, LAZY, tùy chọn.

## TimeEntry

- `employee` → `Employee`: `ManyToOne`, LAZY, bắt buộc.
- `payrollInvoice` → `Invoice`: `ManyToOne`, LAZY, tùy chọn.

## TripStop

- `load` → `Load`: `ManyToOne`, LAZY, bắt buộc.
- `trip` → `Trip`: `ManyToOne`, LAZY, bắt buộc.

## ConditionDefect

- `loadConditionReport` → `LoadConditionReport`: `ManyToOne`, LAZY, bắt buộc.

## ConversationParticipant

- `conversation` → `Conversation`: `ManyToOne`, LAZY, bắt buộc.
- `employee` → `Employee`: `ManyToOne`, LAZY, bắt buộc.

## Document

- `accidentReport` → `AccidentReport`: `ManyToOne`, LAZY, tùy chọn.
- `dvirReport` → `DvirReport`: `ManyToOne`, LAZY, tùy chọn.
- `uploadedBy` → `Employee`: `ManyToOne`, LAZY, bắt buộc.
- `employee` → `Employee`: `ManyToOne`, LAZY, tùy chọn.
- `loadConditionReport` → `LoadConditionReport`: `ManyToOne`, LAZY, tùy chọn.
- `load` → `Load`: `ManyToOne`, LAZY, tùy chọn.
- `maintenanceRecord` → `MaintenanceRecord`: `ManyToOne`, LAZY, tùy chọn.
- `tripStop` → `TripStop`: `ManyToOne`, LAZY, tùy chọn.
- `truck` → `Truck`: `ManyToOne`, LAZY, tùy chọn.

## DriverLicense

- `document` → `Document`: `ManyToOne`, LAZY, tùy chọn.
- `employee` → `Employee`: `ManyToOne`, LAZY, bắt buộc.

## DvirDefect

- `dvirReport` → `DvirReport`: `ManyToOne`, LAZY, bắt buộc.
- `correctedBy` → `Employee`: `ManyToOne`, LAZY, tùy chọn.

## InvoiceLineItem

- `invoice` → `Invoice`: `ManyToOne`, LAZY, bắt buộc.

## MaintenancePart

- `maintenanceRecord` → `MaintenanceRecord`: `ManyToOne`, LAZY, bắt buộc.

## MessageReadReceipt

- `readBy` → `Employee`: `ManyToOne`, LAZY, bắt buộc.
- `message` → `Message`: `ManyToOne`, LAZY, bắt buộc.
