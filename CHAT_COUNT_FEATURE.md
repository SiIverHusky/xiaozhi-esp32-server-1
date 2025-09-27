# Chat Count Feature Implementation

## Overview
This feature provides two chat count functionalities:
1. **Date-specific chat count query**: Allows superusers to view chat count statistics for users on a specific date
2. **User table chat statistics**: Shows "Last 3 months" and "Current month" chat counts directly in the user management table

Both features use direct SQL queries to aggregate chat data and display it in the User Management page.

## Features

### 1. Date-Specific Chat Count Query
- Date picker for selecting query date
- Minimum chat count threshold filter
- Pop-up dialog showing results
- Uses stored procedure logic via direct SQL

### 2. User Table Chat Statistics
- **Last 3 Months**: Shows chat count for last 90 days
- **Current Month**: Shows chat count from 1st of current month to now
- **Refresh Button**: Updates chat statistics on demand
- **Auto-load**: Statistics load automatically when page opens
- **Real-time merge**: Chat stats are merged with user data dynamically

## Implementation Approach
After testing, we found that MyBatis had issues with stored procedure calls, so the implementation uses a direct SQL query instead of calling the stored procedure. This provides the same functionality with better reliability.

## Files Modified/Created

### Frontend (Vue.js)
1. **UserManagement.vue** - Enhanced with chat count features
   - Date picker and controls for date-specific queries
   - Two new table columns: "Last 3 Months" and "Current Month"
   - Refresh button for updating chat statistics
   - Dialog to display date-specific query results
   - Auto-loading chat stats on page load
   - Added debugging console logs

2. **admin.js** - Added API methods for calling backend endpoints
   - `getChatCount(date, minCount, callback)` method for date queries
   - `getUserChatStats(callback)` method for user table statistics
   - Added debugging console logs

3. **main.js** - Updated Element UI locale configuration
   - Changed from default Chinese to English locale
   - Affects all Element UI components including date picker

4. **locale-options.js** - Created reference file with locale configuration examples

### Backend (Spring Boot/Java)
1. **AdminController.java** - Added REST endpoints
   - `GET /admin/chat-count` endpoint for date-specific queries
   - `GET /admin/users/chat-stats` endpoint for user table statistics
   - Both endpoints have superuser permission checks and logging

2. **ChatCountVO.java** - Value object for date-specific query results
   - Contains userId, username, and chatCount fields

3. **UserChatStatsVO.java** - NEW: Value object for user table statistics
   - Contains userId, last3MonthsCount, and currentMonthCount fields

4. **SysUserService.java** - Added service interface methods
   - `getChatCount(String date, Integer minCount)` method
   - `getUserChatStats()` method for user table statistics

5. **SysUserServiceImpl.java** - Implemented the service methods
   - Calls DAO layer to execute SQL queries
   - Added comprehensive logging for debugging

6. **SysUserDao.java** - Added DAO interface methods
   - `getChatCount(@Param("date") String date, @Param("minCount") Integer minCount)` method
   - `getUserChatStats()` method for retrieving all user chat statistics

7. **SysUserDao.xml** - Added MyBatis SQL mappings
   - Direct SQL query for date-specific chat counts
   - Complex JOIN query for user table statistics with period calculations
   - Proper result maps for field mapping

## SQL Queries Used

### Date-Specific Chat Count Query
```sql
SELECT su.id AS userId,
       su.username,
       COUNT(*) AS chatCount
FROM ai_agent_chat_history c
JOIN ai_device d ON c.mac_address = d.mac_address
JOIN sys_user su ON d.user_id = su.id
WHERE DATE(c.created_at) = #{date}
GROUP BY su.id, su.username
HAVING chatCount > #{minCount}
ORDER BY chatCount DESC
```

### User Table Chat Statistics Query
```sql
SELECT 
    su.id AS userId,
    COALESCE(last3months.chat_count, 0) AS last3MonthsCount,
    COALESCE(currentmonth.chat_count, 0) AS currentMonthCount
FROM sys_user su
LEFT JOIN (
    SELECT d.user_id, COUNT(*) as chat_count
    FROM ai_agent_chat_history c
    JOIN ai_device d ON c.mac_address = d.mac_address
    WHERE c.created_at >= DATE_SUB(CURDATE(), INTERVAL 90 DAY)
    GROUP BY d.user_id
) last3months ON su.id = last3months.user_id
LEFT JOIN (
    SELECT d.user_id, COUNT(*) as chat_count
    FROM ai_agent_chat_history c
    JOIN ai_device d ON c.mac_address = d.mac_address
    WHERE c.created_at >= CONCAT(YEAR(CURDATE()), '-', LPAD(MONTH(CURDATE()), 2, '0'), '-01 00:00:00')
              AND c.created_at <= NOW()
    GROUP BY d.user_id
) currentmonth ON su.id = currentmonth.user_id
ORDER BY su.id
```

## Field Mapping
The implementation uses proper camelCase field names:
- Database `su.id` → Java `userId` (Long)
- Database `su.username` → Java `username` (String)  
- Database `COUNT(*)` → Java `chatCount` (Integer)

A MyBatis result map ensures proper field mapping between SQL columns and Java object properties.

## Security
- The endpoint is protected with `@RequiresPermissions("sys:role:superAdmin")`
- Only superusers can access the chat count statistics

## Debugging
The implementation includes comprehensive logging:
- Frontend: Console logs for API calls and responses
- Backend: Detailed logging in controller and service layers
- Shows parameters, response data, and error details

## Usage

### Date-Specific Chat Count Query
1. Navigate to the User Management page
2. Select a date using the date picker
3. Optionally set a minimum chat count threshold
4. Click "Get Chat Counts" button
5. View results in the popup dialog showing user ID, username, and chat count

### User Table Chat Statistics
1. Navigate to the User Management page
2. View "Last 3 Months" and "Current Month" columns in the user table
3. Click "Refresh Chat Stats" button to update the statistics
4. Statistics show automatically when page loads

## API Endpoints
```
GET /admin/chat-count?date=YYYY-MM-DD&minCount=0
GET /admin/users/chat-stats
```

## Troubleshooting
If you see "暂无数据" (No data available), check:
1. Browser console for API call logs
2. Server logs for detailed execution information
3. Database connectivity and table structure
4. Date format (should be YYYY-MM-DD)
5. User permissions (must be superuser)

### Common Issues Fixed:
- **Only username showing**: Fixed by using camelCase field names (userId, chatCount) and explicit MyBatis result map
- **Empty results**: Ensure the date matches records in ai_agent_chat_history table
- **Field mapping errors**: The implementation now uses proper Java naming conventions with explicit column mapping
- **Current month calculation incorrect**: Fixed SQL query to use proper date/time functions with explicit time boundaries
- **Chat History dialog not working**: Fixed API parameter passing (GET requests should use query parameters, not data body)

### Latest Fixes (September 6, 2025):
1. **Current Month Query Fixed**: Changed from `DATE_FORMAT(CURDATE(), '%Y-%m-01')` to `CONCAT(YEAR(CURDATE()), '-', LPAD(MONTH(CURDATE()), 2, '0'), '-01 00:00:00')` with upper bound `<= NOW()` for more accurate current month calculations
2. **Chat History Dialog Debugging**: Added comprehensive console logging and fixed API parameter passing for GET requests

## Database Dependency
This feature queries the following tables:
- `ai_agent_chat_history` - Contains chat records with mac_address and created_at
- `ai_device` - Links mac_address to user_id  
- `sys_user` - Contains user information with id and username

The stored procedure `getChatCount` is still supported but not used by default due to MyBatis compatibility issues.
