import { api } from './client'
import type {
  Complaint,
  ComplaintFilter,
  ComplaintListResponse,
  ChangeStatusRequest,
  DuplicateCandidatesResponse,
  ProblemCategory,
} from './types'

const PAGE_SIZE = 20

export async function listComplaints(filter: ComplaintFilter): Promise<ComplaintListResponse> {
  const params: Record<string, string | number> = {
    sort: filter.sort,
    page: filter.page,
    size: PAGE_SIZE,
  }
  if (filter.status) params.status = filter.status
  if (filter.slaBreached) params.slaBreached = 'true'
  if (filter.category) params.category = filter.category
  if (filter.district) params.district = filter.district
  const res = await api.get<ComplaintListResponse>('/complaints', { params })
  return res.data
}

export async function getComplaint(id: number): Promise<Complaint> {
  const res = await api.get<Complaint>(`/complaints/${id}`)
  return res.data
}

export async function changeStatus(id: number, req: ChangeStatusRequest): Promise<Complaint> {
  const res = await api.patch<Complaint>(`/complaints/${id}/status`, req)
  return res.data
}

export async function findDuplicates(
  lat: number,
  lon: number,
  category: ProblemCategory,
): Promise<DuplicateCandidatesResponse> {
  const res = await api.get<DuplicateCandidatesResponse>('/complaints/duplicates', {
    params: { lat, lon, category },
  })
  return res.data
}
