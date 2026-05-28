export type UserRole = 'RESIDENT' | 'OPERATOR' | 'ADMIN'

export interface UserResponse {
  id: number
  email: string
  role: UserRole
  fullName?: string | null
  emailVerified: boolean
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  accessExpiresIn: number
  refreshExpiresIn: number
  user: UserResponse
}

export interface LoginResponse {
  requires2fa: boolean
  challengeToken?: string | null
  challengeExpiresIn?: number | null
  auth?: AuthResponse | null
}

export interface MessageResponse {
  message: string
}

export interface ApiError {
  code: string
  message: string
}

// --- Жалобы (Day 16) ---

export type ComplaintStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED' | 'DUPLICATE'

export type ProblemCategory =
  | 'GARBAGE' | 'ROADS' | 'SIDEWALKS' | 'LIGHTING' | 'GREENERY' | 'LANDSCAPING'
  | 'PLAYGROUNDS' | 'PARKS' | 'BEACHES' | 'SAFETY' | 'VANDALISM' | 'WATER_SUPPLY'
  | 'SEWAGE' | 'ELECTRICITY' | 'ECOLOGY' | 'ACCESSIBILITY' | 'TRADE' | 'OTHER'

export type ComplaintSort = 'date' | 'votes' | 'priority'

export interface ComplaintPhoto {
  id: number
  photoUrl: string
  thumbUrl: string
  sortOrder: number
}

export interface StatusChange {
  fromStatus?: ComplaintStatus | null
  toStatus: ComplaintStatus
  comment: string
  changedByName?: string | null
  createdAt: string
}

export interface Complaint {
  id: number
  authorId: number
  authorName?: string | null
  category: ProblemCategory
  title: string
  description: string
  latitude: number
  longitude: number
  address: string
  district?: string | null
  status: ComplaintStatus
  photos: ComplaintPhoto[]
  votesCount: number
  userVoted: boolean
  duplicateOfId?: number | null
  createdAt: string
  updatedAt: string
  resolvedAt?: string | null
  statusHistory: StatusChange[]
  slaDeadline?: string | null
  slaBreached: boolean
}

export interface ComplaintListResponse {
  items: Complaint[]
  page: number
  size: number
  total: number
}

export interface DuplicateCandidate {
  id: number
  title: string
  category: ProblemCategory
  status: ComplaintStatus
  address: string
  votesCount: number
  distanceMeters: number
  createdAt: string
}

export interface DuplicateCandidatesResponse {
  items: DuplicateCandidate[]
}

export interface MonthlyKpis {
  total: number
  prevTotal: number
  avgResolutionHours: number | null
  prevAvgResolutionHours: number | null
  resolvedWithin7dPct: number | null
  prevResolvedWithin7dPct: number | null
  newCount: number
  inProgressCount: number
  resolvedCount: number
  rejectedCount: number
  duplicateCount: number
}

export interface AnalyticsOverview {
  total: number
  new: number
  inProgress: number
  resolved: number
  rejected: number
  duplicate: number
  today: number
  week: number
  slaBreachCount: number
  monthlyKpis: MonthlyKpis
}

export type AnalyticsPeriod = 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR' | 'ALL'

export interface DailyPoint {
  date: string
  created: number
  resolved: number
}

export interface TrendPoint {
  bucketStart: string
  value: number
}

export interface TrendsResponse {
  days: DailyPoint[]
  createdSeries?: TrendPoint[]
  resolvedSeries?: TrendPoint[]
  groupBy?: 'day' | 'week' | 'month'
}

export interface CategoryStat {
  category: ProblemCategory
  label: string
  count: number
  sharePct: number
  avgResolutionHours: number | null
  medianResolutionHours?: number | null
  p90ResolutionHours?: number | null
  slaCompliancePct?: number | null
}

export interface DistrictStat {
  district: string
  label: string
  count: number
  newCount: number
  resolvedCount: number
  medianResolutionHours?: number | null
  slaCompliancePct?: number | null
}

export interface SlaStat {
  category: ProblemCategory
  label: string
  slaHours: number
  avgResolutionHours: number | null
  breachPct: number
  resolvedCount: number
}

export interface VotesBucket {
  bucket: string
  count: number
  avgResolutionHours: number | null
}

export interface OperationalSnapshot {
  backlog: number
  overdueNow: number
  avgDtaHours24h: number | null
  dtaTargetHours: number
  createdToday: number
  createdYesterday: number
  statusBreakdown: Record<string, number>
}

export interface BurningComplaintItem {
  id: number
  title: string
  districtCode: string | null
  category: string
  createdAt: string
  slaDueAt: string
  secondsToDeadline: number
}

export interface StrategicKpis {
  slaCompliancePct: number
  slaTargetPct: number
  medianResolutionHours: number | null
  p90ResolutionHours: number | null
  reopenRate: number
  reopenTargetPct: number
  throughput: number
}

export interface ReopenStat {
  reopenRate: number
  reopenCount: number
  resolvedCount: number
}

export interface ChangeStatusRequest {
  toStatus: ComplaintStatus
  comment: string
  duplicateOfId?: number | null
}

export interface ComplaintFilter {
  status: ComplaintStatus | null
  slaBreached: boolean
  category: ProblemCategory | null
  district: string | null
  sort: ComplaintSort
  page: number
}

// --- Объявления (Day 17B) ---

export type IconStyle = 'INFO' | 'SUCCESS' | 'WARNING'

export interface Announcement {
  id: number
  title: string
  body: string
  iconStyle: IconStyle
  category: ProblemCategory | null
  districts: string[]
  authorId: number
  publishedAt: string
  expiresAt: string | null
}

export interface AnnouncementsListResponse {
  items: Announcement[]
  total: number
}

export interface CreateAnnouncementRequest {
  title: string
  body: string
  iconStyle: IconStyle
  districts: string[]
  expiresAt?: string
}

// --- Settings: команда + audit-лог (Day 17C) ---

export type TeamStatus = 'ACTIVE' | 'FROZEN' | 'PENDING'

export interface TeamMemberDto {
  id: number
  email: string
  fullName: string | null
  role: 'ADMIN' | 'OPERATOR'
  district: string | null
  status: TeamStatus
  createdAt: string
  lastLoginAt: string | null
  invitedAt: string | null
}

export interface TeamMembersResponse {
  items: TeamMemberDto[]
}

export interface AuditEntryDto {
  id: number
  timestamp: string
  actorEmail: string | null
  action: string
  targetType: string | null
  targetId: string | null
  ip: string | null
  details: string | null
}

export interface AuditLogResponse {
  items: AuditEntryDto[]
}
