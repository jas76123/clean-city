export type UserRole = 'RESIDENT' | 'OPERATOR' | 'INSPECTOR' | 'ADMIN'

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
